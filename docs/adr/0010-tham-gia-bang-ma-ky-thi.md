# ADR-0010: Tham gia kỳ thi bằng mã

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15 (cập nhật 2026-11-18: bỏ đổi mã tham gia)

## Bối cảnh

Cần cơ chế để học sinh vào đúng kỳ thi. Các cách thường gặp: nhập mã, giáo viên giao theo danh sách, hoặc kết hợp cả hai. MVP **chưa có module lớp học**, nên chưa có dữ liệu danh sách học sinh để giao bài.

## Quyết định

- Mỗi kỳ thi (`ExamSession`) có **mã tham gia** 6 ký tự, do hệ thống sinh. Mã thuộc về kỳ thi, không thuộc về đề.
- Học sinh **phải đăng nhập**, sau đó nhập mã để **tham gia** (`SessionParticipant`) và vào phòng chờ. Khi kỳ thi mở, học sinh bắt đầu làm bài (`Attempt`). Tách "tham gia" khỏi "làm bài" để giáo viên thấy được ai đang chờ, ai đang làm.
- Mã được sinh một lần khi tạo kỳ thi và **không đổi** tới hết kỳ thi. Mã chỉ có hiệu lực khi kỳ thi chưa đóng và còn cho tham gia.
- Mã bị lộ thì giáo viên **khóa tham gia** khi lớp đã vào đủ; người ngoài đã lỡ bắt đầu làm bài thì giáo viên khóa bài trên màn hình giám sát.
- **Giới hạn nhập sai:** một tài khoản nhập sai quá 5 lần trong 15 phút thì bị chặn nhập mã 15 phút (`ACCESS_CODE_ATTEMPTS_EXCEEDED`, `429`).
- MVP mỗi học sinh một lượt làm bài cho mỗi kỳ thi.

## Phương án đã cân nhắc

- **Giao theo danh sách lớp:** kiểm soát chặt và biết ai vắng, nhưng cần module `classroom`, chưa có ở MVP.
- **Kết hợp mã và danh sách cho phép:** chặt nhất nhưng phức tạp nhất.
- **Mã gắn với đề thay vì kỳ thi:** sai ranh giới; đề thuộc `examdesign`, học sinh không bao giờ chạm vào.
- **Cho giáo viên đổi mã khi bị lộ:** mã thường chỉ lộ cho vài người, nhưng đổi mã thì phải báo lại mã mới cho mọi học sinh chưa vào phòng chờ, và mã mới phát lại theo cùng cách nên có thể lộ tiếp; khóa tham gia đơn giản hơn.

## Hệ quả

- (+) Đơn giản, tổ chức thi nhanh, không cần chuẩn bị danh sách.
- (+) Chuyển sang kết hợp mã và danh sách sau này chỉ cần thêm một bước kiểm tra lúc tham gia, `proctoring` không phải sửa.
- (−) Ai có mã và có tài khoản là vào được; dựa vào việc giáo viên khóa tham gia khi cần.
- (−) Mã lộ trước giờ thi mà vẫn muốn nhận người thì không thay được mã; chỉ có thể hủy kỳ thi rồi tạo kỳ thi mới.
- (−) **Không biết ai lẽ ra phải thi mà vắng**; màn hình giám sát chỉ thấy người đã nhập mã.
- Xem lại khi: có module lớp học, hoặc cần thống kê vắng thi.
