# State machine

Các thực thể có vòng đời trong hệ thống, trạng thái của chúng, và thao tác nào được phép ở trạng thái nào. Luật chuyển trạng thái nằm trong chính entity ([ADR-0008](adr/0008-khong-lombok-mapstruct.md)): mỗi thao tác là một hàm hành vi, kiểm tra điều kiện trước khi đổi trạng thái.

Thiết kế cơ sở dữ liệu tương ứng: [database.md](database.md).

## 1. Kỳ thi (`ExamSession`, module `examination`)

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED : create()
    SCHEDULED --> OPEN : open()
    SCHEDULED --> CANCELLED : cancel() (giáo viên, hoặc job)
    OPEN --> CLOSED : close()
    CLOSED --> [*]
    CANCELLED --> [*]
```

| Thao tác | Chuyển | Ai, khi nào | Điều kiện | Tác động |
|---|---|---|---|---|
| `create` | → `SCHEDULED` | Giáo viên | Đề tồn tại, chưa lưu trữ, có ít nhất 1 câu (hỏi `ExamDesignApi`); `start_at < end_at`; `end_at` ở tương lai | Lưu `exam_id`, sao chép tên đề; sinh `access_code`; `join_open = true` |
| `open` | `SCHEDULED` → `OPEN` | Giáo viên bấm "Bắt đầu", hoặc job khi tới `start_at` | `now < end_at`; đề vẫn dùng được: tồn tại, chưa lưu trữ, có ít nhất 1 câu (hỏi `ExamDesignApi`) | Chụp snapshot câu hỏi và thời lượng ([ADR-0003](adr/0003-snapshot-de-khi-mo-ky-thi.md)); ghi `opened_at` |
| `close` | `OPEN` → `CLOSED` | Job khi quá `end_at` + 5 giây ân hạn và không còn bài được cộng giờ đang làm ([ADR-0018](adr/0018-cong-gio-vuot-gio-dong-ky-thi.md)), hoặc giáo viên kết thúc sớm | — | `join_open = false`; mọi bài `IN_PROGRESS` chuyển `EXPIRED`; job ghi `closed_at = max(end_at, hạn chót muộn nhất)` |
| `cancel` | `SCHEDULED` → `CANCELLED` | Giáo viên bất cứ lúc nào trước khi mở; hoặc job khi tới `start_at` mà đề không còn dùng được, hay khi quá `end_at` mà kỳ thi chưa từng mở | — | Mã tham gia hết hiệu lực; không có bài làm nào |
| `lockJoin` / `unlockJoin` | không đổi | Giáo viên | `SCHEDULED` hoặc `OPEN` | Đổi `join_open` |
| `changeResultVisibility` | không đổi | Giáo viên | Mọi trạng thái | Đổi những gì học sinh được xem sau khi bài kết thúc (`HIDDEN`, `SCORE`, `SCORE_AND_ANSWERS`); đáp án chỉ hiện khi kỳ thi đã `CLOSED` |

Mã tham gia sinh một lần khi tạo và không đổi; mã bị lộ thì giáo viên khóa tham gia ([ADR-0010](adr/0010-tham-gia-bang-ma-ky-thi.md)).

**Đề không còn dùng được** (giáo viên lưu trữ đề hoặc bỏ hết câu sau khi lên lịch kỳ thi):
1. **Trước `start_at`:** job không đụng tới kỳ thi. Response kỳ thi có `examUsable = false` để giáo viên biết và kịp sửa đề hoặc hủy, tạo kỳ thi khác. `examUsable` chỉ có ở kỳ thi `SCHEDULED`, tính lúc đọc (một lần hỏi `ExamDesignApi` cho cả trang danh sách); các trạng thái khác để rỗng. Giáo viên bấm mở thủ công thì thấy lỗi `EXAM_NOT_AVAILABLE`.
2. **Tới `start_at`:** job thử mở; đề vẫn không dùng được thì **hủy kỳ thi** (`CANCELLED`), vì không có câu hỏi để chụp và kỳ thi không thể diễn ra.

**Kỳ thi chưa từng mở thì không bao giờ thành `CLOSED`.** Nếu job không chạy suốt khoảng `start_at`–`end_at` (ví dụ ứng dụng tắt), lượt đầu tiên sau đó thấy kỳ thi đã quá giờ đóng mà vẫn `SCHEDULED`: kỳ thi không diễn ra nên bị hủy. Nếu ứng dụng chạy lại khi còn trong khoảng giờ thi thì job mở kỳ thi bình thường.

Job chờ hết 5 giây ân hạn sau `end_at` mới đóng, vì học sinh còn được nộp trong ân hạn ([ADR-0013](adr/0013-xu-ly-het-gio-lam-bai.md)). Bài được giáo viên cộng giờ có thể có hạn chót sau `end_at`; khi đó job chờ bài đó hết hạn chót cộng ân hạn rồi mới đóng, nên kỳ thi có thể còn `OPEN` sau `end_at` ([ADR-0018](adr/0018-cong-gio-vuot-gio-dong-ky-thi.md)). Sau `end_at` không ai bắt đầu bài mới được.

**Thao tác được phép theo trạng thái:**

| | `SCHEDULED` | `OPEN` | `CLOSED` | `CANCELLED` |
|---|---|---|---|---|
| Học sinh nhập mã tham gia | ✓ nếu `join_open` | ✓ nếu `join_open` | ✗ | ✗ |
| Học sinh bắt đầu làm bài | ✗ | ✓ | ✗ | ✗ |
| Khóa/mở tham gia | ✓ | ✓ | ✗ | ✗ |
| Giám sát | — | ✓ | xem lại | — |
| Xem kết quả | — | bài đã kết thúc | ✓ | — |

Sai trạng thái trả `INVALID_SESSION_STATE`; nhập mã khi kỳ thi không nhận tham gia trả `INVALID_ACCESS_CODE`; đề không dùng được (không tồn tại, đã lưu trữ, không có câu nào) khi tạo hoặc mở kỳ thi trả `EXAM_NOT_AVAILABLE`.

## 2. Bài làm (`Attempt`, module `examination`)

```mermaid
stateDiagram-v2
    [*] --> IN_PROGRESS : start()
    IN_PROGRESS --> IN_PROGRESS : saveAnswer(), extendTime()
    IN_PROGRESS --> SUBMITTED : submit()
    IN_PROGRESS --> EXPIRED : expire() (job hoặc kỳ thi đóng)
    IN_PROGRESS --> LOCKED : lock()
    SUBMITTED --> [*]
    EXPIRED --> [*]
    LOCKED --> [*]
```

| Thao tác | Chuyển | Ai | Điều kiện | Tác động |
|---|---|---|---|---|
| `start` | → `IN_PROGRESS` | Học sinh | Kỳ thi `OPEN`; đã tham gia; chưa có bài làm trong kỳ thi này | `deadline = min(now + thời lượng, end_at)` ([ADR-0013](adr/0013-xu-ly-het-gio-lam-bai.md)) |
| `saveAnswer` | giữ `IN_PROGRESS` | Học sinh | `now ≤ deadline + 5 giây` | Thêm hoặc sửa đáp án của một câu |
| `extendTime` | giữ `IN_PROGRESS` | Giáo viên | `now ≤ deadline`; số phút > 0, không giới hạn | `deadline` cộng thêm số phút, được vượt `end_at` ([ADR-0018](adr/0018-cong-gio-vuot-gio-dong-ky-thi.md)) |
| `submit` | → `SUBMITTED` | Học sinh | `now ≤ deadline + 5 giây` | Chấm điểm; `submitted_at = now` |
| `expire` | → `EXPIRED` | Job, hoặc khi kỳ thi đóng | Job: quá hạn chót + 5 giây ân hạn; hoặc kỳ thi đóng | Chấm điểm; `submitted_at = deadline` (hoặc lúc đóng nếu sớm hơn) |
| `lock` | → `LOCKED` | Giáo viên, khi học sinh vi phạm | Bài đang `IN_PROGRESS` (bài đã kết thúc thì không khóa được) | Kết thúc ngay, chấm điểm; `submitted_at = min(now, deadline)` |

- Cả ba trạng thái kết thúc đều **có điểm**, tính từ các đáp án đã lưu. Trạng thái cho biết bài kết thúc bằng cách nào và do ai: `SUBMITTED` là học sinh tự nộp, `EXPIRED` là hệ thống chốt khi hết giờ, `LOCKED` là giáo viên khóa vì vi phạm. Vì vậy không cần cột riêng ghi "ai kết thúc bài".
- Thao tác lên bài đã kết thúc trả lỗi theo trạng thái: `SUBMITTED` → `ATTEMPT_ALREADY_SUBMITTED`, `EXPIRED` hoặc quá hạn chót → `ATTEMPT_EXPIRED`, `LOCKED` → `ATTEMPT_LOCKED`.
- Bắt đầu làm bài khi chưa tham gia trả `NOT_A_PARTICIPANT`; bắt đầu lần thứ hai trả `ATTEMPT_ALREADY_STARTED`.
- Mọi thay đổi đều làm tăng `version` (kể cả lưu đáp án), để phát hiện thao tác đồng thời.
- Khi sau này có câu hỏi tự luận, sẽ cần thêm trạng thái chờ chấm giữa lúc kết thúc và lúc có điểm.

## 3. Câu hỏi và đề (`Question`, `Exam`, module `examdesign`)

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : tạo mới
    ACTIVE --> ACTIVE : sửa
    ACTIVE --> ARCHIVED : xóa (lưu trữ)
    ARCHIVED --> [*]
```

Trạng thái được lưu bằng cột `archived_at` (rỗng là `ACTIVE`), không xóa hẳn dòng.

**Lưu trữ nghĩa là gì:** giáo viên không muốn câu hỏi hoặc đề đó được dùng cho bất kỳ thứ gì **chưa bắt đầu** nữa. Trên giao diện đây là nút "Xóa". Xóa mềm chỉ để giữ lịch sử, không phải để tiếp tục dùng. Đề chỉ sai vài câu thì **sửa đề**, không lưu trữ.

- Câu hỏi và đề đã lưu trữ không hiện trong danh sách và không thêm được vào đề mới hay kỳ thi mới.
- **Kỳ thi đã lên lịch (`SCHEDULED`) không mở được** nếu đề bị lưu trữ sau khi tạo kỳ thi (`EXAM_NOT_AVAILABLE`): giáo viên đã bỏ đề thì không kỳ thi nào chưa bắt đầu được dùng nó. `examdesign` không biết `examination` ([ADR-0004](adr/0004-public-api-va-chieu-phu-thuoc.md)) nên không cảnh báo được lúc lưu trữ; việc cảnh báo do `examination` làm (mục 1).
- Kỳ thi đã mở hoặc đã đóng **không bị ảnh hưởng**, vì đã chụp snapshot đề ([ADR-0003](adr/0003-snapshot-de-khi-mo-ky-thi.md)). Xóa mềm giữ được dòng gốc để truy vết `exam_id` và `source_question_id`.
- Câu hỏi đang nằm trong một đề chưa lưu trữ thì không lưu trữ được, phải bỏ khỏi đề trước.

## 4. Những thứ không phải state machine

| Khái niệm | Vì sao không có trạng thái riêng |
|---|---|
| Tham gia kỳ thi (`SessionParticipant`) | Có dòng là đã tham gia; không có vòng đời nào khác |
| "Chưa vào thi" trên màn hình giám sát | Tính lúc đọc: đã tham gia nhưng chưa có bài làm |
| Online / mất kết nối | Tính lúc đọc: `now − last_seen_at` so với 45 giây ([ADR-0011](adr/0011-giam-sat-hoi-qua-examination-api.md)) |
| Số vi phạm | Bộ đếm, không phải trạng thái; giáo viên tự quyết định ([ADR-0012](adr/0012-mat-ket-noi-khong-tu-dong-xu-ly.md)) |
