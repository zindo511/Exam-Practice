# ADR-0019: MVP không có heartbeat, không phát hiện mất kết nối

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-25
- **Thay thế một phần:** [ADR-0011](0011-giam-sat-hoi-qua-examination-api.md) (heartbeat, `proctor_presence`, `?since=`), [ADR-0012](0012-mat-ket-noi-khong-tu-dong-xu-ly.md) (hiển thị và ghi nhật ký mất kết nối)

## Bối cảnh

ADR-0011 định cho trình duyệt học sinh gọi API heartbeat định kỳ (dự kiến 15 giây một lần); quá 45 giây không thấy thì màn hình giám sát hiện "mất kết nối", và khi học sinh quay lại thì ghi vào nhật ký.

Khi làm thì thấy:
- Tín hiệu dễ sai: tab chạy nền lâu bị trình duyệt làm chậm timer, có thể bị báo mất kết nối dù vẫn có mạng.
- Dòng nhật ký ghi theo tín hiệu sai lại trông như bằng chứng.
- Học sinh mất kết nối rồi không quay lại thì nhật ký không có dòng nào, vì không có heartbeat nào kích hoạt.
- Cách gửi này sẽ bị bỏ khi chuyển sang WebSocket.

## Quyết định

- **MVP không có heartbeat.** Không có API heartbeat, bảng `proctor_presence`, trạng thái online/mất kết nối, sự kiện `DISCONNECTED` và `RECONNECTED`.
- `proctoring` chỉ lưu `proctor_event`: sự kiện trình duyệt báo khi xảy ra (rời tab, quay lại tab, thoát toàn màn hình) và thao tác của giáo viên.
- **Mỗi sự kiện từ trình duyệt đều hỏi** `ExaminationApi.findAttemptOfStudent` để xác nhận bài làm thuộc về học sinh đang gửi. Sự kiện ít xảy ra nên không cần lưu lại kết quả kiểm tra.
- **Số vi phạm đếm từ `proctor_event`** lúc đọc, không lưu bộ đếm riêng.
- **Không có API lấy sự kiện mới của cả lớp theo mốc thời gian (`?since=`).** Bảng giám sát cập nhật khoảng 5 giây một lần đã có số vi phạm, chi tiết xem ở nhật ký từng học sinh. Lấy theo mốc thời gian còn dễ sót: sự kiện có `received_at` trước lần hỏi nhưng commit sau lần hỏi sẽ bị bỏ qua.
- Phát hiện mất kết nối làm cùng WebSocket sau MVP, bằng một ADR mới. Khi đó vẫn cần ping/pong của giao thức WebSocket, vì rớt mạng đột ngột không báo đóng kết nối.

Giữ nguyên: trạng thái bài làm hỏi `ExaminationApi`, giáo viên xem bằng polling, không có event giữa module (ADR-0011); hệ thống không tự xử lý, giáo viên quyết định (ADR-0012).

## Phương án đã cân nhắc

- **Giữ heartbeat, chỉ hiển thị mà không ghi nhật ký:** giáo viên vẫn thấy ai mất kết nối, nhưng vẫn có thể báo nhầm và sẽ phải thay cách gửi khi có WebSocket.
- **Làm WebSocket ngay:** tín hiệu tốt hơn, nhưng là việc lớn ngoài phạm vi MVP.

## Hệ quả

- (+) Ít code và ít bảng hơn; không có bộ đếm nhiều request cùng ghi.
- (+) Không ghi bằng chứng sai.
- (−) Giáo viên không biết ai mất kết nối; học sinh phải tự báo để được cộng giờ.
- (−) Không biết học sinh đang xem câu nào.
- (−) Không có dòng sự kiện của cả lớp; giáo viên mở nhật ký từng học sinh.
- (−) Học sinh chặn request gửi sự kiện thì giáo viên không nhận ra, vì không còn tín hiệu định kỳ để thấy im lặng bất thường.
