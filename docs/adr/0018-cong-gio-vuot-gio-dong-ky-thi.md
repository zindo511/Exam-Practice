# ADR-0018: Cộng giờ được vượt giờ đóng kỳ thi, kỳ thi chờ bài cuối cùng hết giờ

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-25
- **Bổ sung cho:** [ADR-0013](0013-xu-ly-het-gio-lam-bai.md) (job đóng kỳ thi)

## Bối cảnh

Có hai mốc thời gian khác nhau:
- `end_at` của **kỳ thi**: chung cho cả lớp, giáo viên đặt lúc tạo. Sau mốc này không ai bắt đầu làm bài được nữa.
- `deadline` của **bài làm**: riêng từng học sinh, tính lúc bắt đầu bằng `min(giờ bắt đầu + thời lượng, end_at)`. Vào muộn thì `deadline` bị cắt bằng `end_at`, không bao giờ vượt.

Giáo viên cộng giờ cho học sinh mất mạng thật ([ADR-0012](0012-mat-ket-noi-khong-tu-dong-xu-ly.md)) bằng cách dời `deadline`. Khi đó `deadline` có thể vượt `end_at`: đây là cách duy nhất để `deadline > end_at`.

Job ở [ADR-0013](0013-xu-ly-het-gio-lam-bai.md) đóng kỳ thi khi quá `end_at + 5 giây`, và lúc đóng thì chốt **mọi** bài còn đang làm. Bài được cộng giờ tới 9:10 trong kỳ thi đóng lúc 9:00 sẽ bị chốt lúc 9:00:05, mất trắng phần giờ được cộng. Nếu giáo viên đặt `end_at = giờ mở + thời lượng` (thường gặp) thì cộng giờ gần như vô dụng.

## Quyết định

- **Cộng giờ chỉ sửa `deadline` của bài làm**, không đụng `end_at`. Chỉ cộng được khi chưa quá `deadline`; không giới hạn số phút.
- **Job chỉ đóng kỳ thi khi cả hai điều kiện đúng:**
  1. Đã quá `end_at + 5 giây` (như cũ).
  2. Không còn bài `IN_PROGRESS` nào có `deadline + 5 giây` từ bây giờ trở đi.

  Bài được cộng giờ vẫn được bước "chốt bài" của job chốt đúng lúc `deadline + 5 giây` của nó; lượt sau đó kỳ thi đóng.
- **Kỳ thi ghi nhận đóng lúc** `max(end_at, deadline muộn nhất)`.
- **Giáo viên kết thúc sớm** vẫn chốt mọi bài, kể cả bài được cộng giờ: đó là lựa chọn của giáo viên.

Ví dụ: kỳ thi 8:00 đến 9:00, A được cộng 10 phút lúc 8:40, `deadline` của A là 9:10.
- 9:00: không ai bắt đầu bài mới được.
- 9:00:05: kỳ thi chưa đóng vì A còn làm; bài của người khác hết giờ thì chốt như thường.
- 9:10:05: job chốt bài A, rồi đóng kỳ thi, `closed_at = 9:10`.

## Phương án đã cân nhắc

- **Chặn cộng giờ ở `end_at`:** đơn giản, nhưng khi `end_at = giờ mở + thời lượng` thì không cộng được phút nào.
- **Cộng giờ thì dời luôn `end_at` của kỳ thi:** `end_at` cũng là giờ chặn bắt đầu bài, nên dời nó cho phép học sinh đến muộn vào thi thêm.
- **Đóng kỳ thi ở `end_at` nhưng không chốt bài được cộng giờ:** kỳ thi `CLOSED` thì đáp án có thể hiện cho học sinh khác (`SCORE_AND_ANSWERS`) trong khi A vẫn đang làm.

## Hệ quả

- (+) Cộng giờ có tác dụng thật, kể cả khi `deadline` vượt `end_at`.
- (+) Học sinh đến muộn không được lợi, vì `end_at` không đổi.
- (+) Đáp án không bị lộ: kỳ thi chỉ `CLOSED` khi không còn ai làm bài.
- (−) Câu query tìm kỳ thi cần đóng thêm một điều kiện `NOT EXISTS` trên `exam_attempt`.
- (−) Kỳ thi có thể ở `OPEN` sau `end_at`; giao diện không nên coi `end_at` là lúc kỳ thi chắc chắn kết thúc.
