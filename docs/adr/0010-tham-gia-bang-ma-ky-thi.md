# ADR-0010: Tham gia kỳ thi bằng mã

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15 (cập nhật 2026-11-18: bỏ đổi mã tham gia; làm rõ cách đếm lần sai và quay lại phòng chờ)

## Bối cảnh

Cần cơ chế để học sinh vào đúng kỳ thi. Các cách thường gặp: nhập mã, giáo viên giao theo danh sách, hoặc kết hợp cả hai. MVP **chưa có module lớp học**, nên chưa có dữ liệu danh sách học sinh để giao bài.

## Quyết định

- Mỗi kỳ thi (`ExamSession`) có **mã tham gia** 6 ký tự, do hệ thống sinh. Mã thuộc về kỳ thi, không thuộc về đề.
- Học sinh **phải đăng nhập**, sau đó nhập mã để **tham gia** (`SessionParticipant`) và vào phòng chờ. Khi kỳ thi mở, học sinh bắt đầu làm bài (`Attempt`). Tách "tham gia" khỏi "làm bài" để giáo viên thấy được ai đang chờ, ai đang làm.
- Mã được sinh một lần khi tạo kỳ thi và **không đổi** tới hết kỳ thi. Mã chỉ có hiệu lực khi kỳ thi chưa đóng và còn cho tham gia.
- Mã bị lộ thì giáo viên **khóa tham gia** khi lớp đã vào đủ; người ngoài đã lỡ bắt đầu làm bài thì giáo viên khóa bài trên màn hình giám sát.
- **Giới hạn nhập sai:** chỉ tính là một lần sai khi mã **không khớp kỳ thi nào**. Mã đúng của kỳ thi đã khóa tham gia hoặc đã kết thúc vẫn bị từ chối với cùng thông báo (`INVALID_ACCESS_CODE`) nhưng không tính, để học sinh đến muộn bấm thử nhiều lần không bị chặn. Một tài khoản được sai tối đa 5 lần; khi 5 lần sai gần nhất nằm trong 15 phút thì bị chặn nhập mã 15 phút tính từ lần sai cuối (`ACCESS_CODE_ATTEMPTS_EXCEEDED`, `429`). Trong lúc bị chặn, mã không được kiểm tra và không ghi thêm lần sai. Lần sai được ghi trong transaction riêng để không mất khi request trả lỗi.
- **Quay lại phòng chờ không cần mã:** học sinh đã tham gia thấy kỳ thi trong "Kỳ thi của tôi" (`GET /api/exam/my-sessions`). Nhập lại mã của kỳ thi đã tham gia vẫn được, kể cả khi giáo viên đã khóa tham gia.
- MVP mỗi học sinh một lượt làm bài cho mỗi kỳ thi.

## Phương án đã cân nhắc

- **Giao theo danh sách lớp:** kiểm soát chặt và biết ai vắng, nhưng cần module `classroom`, chưa có ở MVP.
- **Kết hợp mã và danh sách cho phép:** chặt nhất nhưng phức tạp nhất.
- **Tính cả mã đúng của kỳ thi đã khóa là một lần sai:** che kín hơn việc mã nào có thật, nhưng phạt oan học sinh đến muộn; mã đoán ra được cũng không dùng để vào được vì kỳ thi đã khóa hoặc kết thúc.
- **Mã gắn với đề thay vì kỳ thi:** sai ranh giới; đề thuộc `examdesign`, học sinh không bao giờ chạm vào.
- **Cho giáo viên đổi mã khi bị lộ:** mã thường chỉ lộ cho vài người, nhưng đổi mã thì phải báo lại mã mới cho mọi học sinh chưa vào phòng chờ, và mã mới phát lại theo cùng cách nên có thể lộ tiếp; khóa tham gia đơn giản hơn.

## Hệ quả

- (+) Đơn giản, tổ chức thi nhanh, không cần chuẩn bị danh sách.
- (+) Chuyển sang kết hợp mã và danh sách sau này chỉ cần thêm một bước kiểm tra lúc tham gia, `proctoring` không phải sửa.
- (−) Ai có mã và có tài khoản là vào được; dựa vào việc giáo viên khóa tham gia khi cần.
- (−) Mã lộ trước giờ thi mà vẫn muốn nhận người thì không thay được mã; chỉ có thể hủy kỳ thi rồi tạo kỳ thi mới.
- (−) **Không biết ai lẽ ra phải thi mà vắng**; màn hình giám sát chỉ thấy người đã nhập mã.
- Xem lại khi: có module lớp học, hoặc cần thống kê vắng thi.
