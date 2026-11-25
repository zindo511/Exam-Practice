# ADR-0012: Mất kết nối không tự động xử lý

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15

## Bối cảnh

Màn hình giám sát hiển thị học sinh "mất kết nối" khi không nhận được heartbeat quá 45 giây. Câu hỏi đặt ra: hệ thống có nên tự động khóa hoặc hủy bài của học sinh đó không?

Thực tế:
- Phần lớn trường hợp mất kết nối là vô tình: wifi chập chờn, máy tắt màn hình, trình duyệt treo, mất điện.
- Tín hiệu không hoàn toàn đáng tin: trình duyệt làm chậm timer của tab chạy nền, heartbeat có thể đến trễ dù học sinh vẫn ở đó.

## Quyết định

- "Mất kết nối" chỉ là **thông tin hiển thị** cho giáo viên, tính lúc đọc, không phải căn cứ để hệ thống tự hành động.
- Mỗi lần mất và có lại kết nối được ghi vào nhật ký `proctor_event` để làm bằng chứng.
- **Giáo viên quyết định**: cộng thêm giờ (mất mạng thật, [ADR-0018](0018-cong-gio-vuot-gio-dong-ky-thi.md)), khóa bài (vi phạm: bài kết thúc ngay và được chấm, đánh dấu `LOCKED`), hoặc không làm gì. Không có thao tác buộc nộp riêng: học sinh không quay lại được thì bài tự chốt khi hết giờ.
- Hệ thống **không có thao tác hủy (xóa) bài**. Nặng nhất là **khóa bài**: học sinh không làm tiếp được nhưng bài làm vẫn được giữ.

Thiết kế hiện có đã tự xử lý hậu quả của việc mất kết nối:

| Điều gì xảy ra | Nhờ cơ chế nào |
|---|---|
| Đáp án đã chọn không bị mất | Tự động lưu lên server sau mỗi lần chọn |
| Mất kết nối không kéo dài được thời gian | Hạn chót tính theo giờ server |
| Quay lại làm tiếp được | Tải lại trang thì server trả về bài làm và thời gian còn lại |
| Không quay lại kịp thì vẫn có điểm | Hết giờ tự nộp với các đáp án đã lưu |

## Phương án đã cân nhắc

- **Tự động hủy hoặc khóa bài khi mất kết nối:** phạt oan học sinh không có lỗi, dựa trên tín hiệu không chắc chắn.
- **Tự động dừng đồng hồ khi mất kết nối:** mở ra cách gian lận (cố ý ngắt mạng để có thêm thời gian).

## Hệ quả

- (+) Không phạt oan; người mất mạng chỉ thiệt phần thời gian bị mất, và giáo viên có thể bù giờ.
- (+) Đúng ranh giới module: `proctoring` chỉ quan sát, mọi thay đổi bài làm đi qua `examination` khi giáo viên ra lệnh.
- (−) Giáo viên phải tự theo dõi và quyết định trong lúc thi.
- Sau này có thể thêm cảnh báo tự động (ví dụ mất kết nối 3 lần trong 10 phút) để làm nổi bật trên màn hình, nhưng vẫn chỉ là gợi ý cho giáo viên.
