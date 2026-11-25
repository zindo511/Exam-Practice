# ADR-0011: Giám sát hỏi trạng thái qua `ExaminationApi`, polling trước

- **Trạng thái:** Đã chấp nhận; phần heartbeat và `proctor_presence` bị thay thế bởi [ADR-0019](0019-mvp-khong-co-heartbeat.md)
- **Ngày:** 2026-11-15

## Bối cảnh

Giáo viên cần thấy trạng thái của cả lớp trong lúc thi: ai đang làm, tiến độ, thời gian còn lại, ai mất kết nối, sự kiện bất thường. Dữ liệu đến từ hai nguồn:
- **Nghiệp vụ làm bài**, thuộc `examination`: trạng thái bài làm, số câu đã trả lời, hạn chót, danh sách thí sinh.
- **Tín hiệu từ trình duyệt học sinh**, chỉ `proctoring` có: heartbeat, rời tab, thoát toàn màn hình.

Ban đầu dự định để `examination` phát event (`AttemptStarted`, `AnswerSaved`, `AttemptSubmitted`…) cho `proctoring` sao chép trạng thái vào bảng riêng. Cách đó có nhiều phần chuyển động (event, listener, bảng sao chép, quy tắc không cho trạng thái đi lùi), và bảng sao chép có thể lệch so với thực tế nếu ứng dụng sập giữa lúc commit và lúc xử lý event, vì event của Spring chỉ nằm trong bộ nhớ.

## Quyết định

**`proctoring` chỉ lưu dữ liệu mà chỉ nó có:**
- `proctor_presence`: mỗi bài làm một dòng (`attempt_id`, `session_id`, `student_id`, `last_seen_at`, câu đang xem), cập nhật bằng heartbeat. Ở heartbeat đầu tiên, `proctoring` hỏi `ExaminationApi.findAttemptOfStudent` để xác nhận bài làm thuộc về học sinh đang đăng nhập, sau đó kiểm tra tại chỗ bằng dòng này.
- `proctor_event`: nhật ký chỉ ghi thêm (rời tab, mất và có lại kết nối…), để xem chi tiết và làm bằng chứng.

**Trạng thái bài làm luôn hỏi từ nguồn gốc.** Mỗi lần giáo viên tải màn hình giám sát:
1. Gọi `ExaminationApi.getSessionAttempts(sessionId, teacherId)`: danh sách thí sinh đã tham gia, trạng thái, số câu đã trả lời, hạn chót.
2. Đọc `proctor_presence` và số vi phạm của kỳ thi đó.
3. Lấy tên học sinh qua `IdentityApi.findUsers(...)`.
4. Ghép theo ID trong bộ nhớ ([ADR-0007](0007-flat-id-linking.md)); tính lúc đọc: `online = now − last_seen_at < 45 giây`, thời gian còn lại = `deadline − now`.

**Không có event giữa các module ở MVP.** `examination` không gọi, không import, không biết `proctoring` tồn tại.

**Truyền dữ liệu tới giáo viên: polling.** Trình duyệt giáo viên gọi API giám sát khoảng 5 giây một lần; sự kiện bất thường mới lấy theo mốc thời gian (`?since=`).

## Phương án đã cân nhắc

- **`examination` phát event, `proctoring` giữ bảng trạng thái sao chép:** tránh được việc hỏi `examination` mỗi lần đọc, nhưng thêm nhiều phần chuyển động và dữ liệu có thể lệch. Ở quy mô MVP, lợi ích không đáng so với chi phí.
- **`proctoring` truy vấn thẳng bảng của `examination`:** vi phạm quyền sở hữu dữ liệu ([ADR-0006](0006-flyway-va-du-lieu-theo-module.md)).
- **`examination` gọi `ProctoringApi` để báo thay đổi:** đảo chiều phụ thuộc ([ADR-0004](0004-public-api-va-chieu-phu-thuoc.md)); phần cốt lõi sẽ phụ thuộc vào phần hay thay đổi.
- **SSE/WebSocket ngay từ đầu:** gần thời gian thực hơn, nhưng độ trễ vài giây của polling chấp nhận được cho MVP.

## Hệ quả

- (+) Trạng thái bài làm trên màn hình giám sát **không bao giờ lệch**, vì luôn đọc từ `examination`.
- (+) Ít phần chuyển động nhất: không event, không listener, không bảng sao chép.
- (+) `examination` hoàn toàn không biết `proctoring`; lỗi ở phần giám sát không ảnh hưởng việc làm bài.
- (−) Mỗi lần polling là một lời gọi `ExaminationApi`, tức một truy vấn khoảng vài chục dòng mỗi 5 giây cho mỗi giáo viên. Rất nhẹ ở quy mô hiện tại.
- (−) Polling có độ trễ bằng chu kỳ hỏi.
- Xem lại khi: chuyển sang SSE/WebSocket (cần tín hiệu "vừa có thay đổi" để đẩy xuống), có module khác cần biết khi bài được nộp, hoặc số kỳ thi giám sát đồng thời lớn đến mức việc hỏi liên tục thành gánh nặng. Khi đó thêm event bằng một ADR mới.
