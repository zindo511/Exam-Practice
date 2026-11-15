# ADR-0003: Chụp snapshot đề khi mở kỳ thi

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15

## Bối cảnh

Giáo viên có thể sửa đề bất cứ lúc nào. Nếu kỳ thi dùng trực tiếp đề gốc trong `examdesign`:
- Học sinh đang làm bài thấy câu hỏi thay đổi giữa chừng; người làm trước và người làm sau gặp hai đề khác nhau.
- Sửa đáp án đúng giữa chừng khiến bài nộp trước và sau bị chấm theo hai đáp án khác nhau.
- Xóa câu hỏi khỏi ngân hàng làm hỏng lịch sử các bài thi cũ.

## Quyết định

- Kỳ thi ở trạng thái **SCHEDULED** chỉ giữ `examId`; sửa đề lúc này vẫn có hiệu lực cho kỳ thi.
- Khi kỳ thi chuyển sang **OPEN**, `examination` gọi `ExamDesignApi` lấy bản sao đề và lưu vào bảng của mình (`exam_session_question`). Từ đó kỳ thi chỉ dùng bản sao.
- Dữ liệu chỉ đi **một chiều, một lần**: `examdesign` không bao giờ đẩy thay đổi sang `examination`, và `examination` không bao giờ ghi vào `examdesign`.
- Bản sao chứa đáp án đúng để server chấm, nhưng dữ liệu gửi xuống trình duyệt học sinh không bao giờ kèm đáp án.

## Phương án đã cân nhắc

- **Dùng trực tiếp đề gốc:** đơn giản nhưng không công bằng và làm hỏng lịch sử.
- **Snapshot ngay lúc lên lịch kỳ thi:** giáo viên sửa đề trước giờ thi sẽ bất ngờ vì kỳ thi không nhận bản mới.
- **`examdesign` đẩy thay đổi sang các kỳ thi đang dùng đề:** phá vỡ chiều phụ thuộc ([ADR-0004](0004-public-api-va-chieu-phu-thuoc.md)) và làm mất ý nghĩa của snapshot.
- **Phiên bản đề (v1, v2…), kỳ thi gắn với một phiên bản:** truy vết tốt hơn, nhưng quá phức tạp cho MVP.

## Hệ quả

- (+) Kỳ thi đang diễn ra và kỳ thi cũ không bị ảnh hưởng khi sửa hoặc xóa đề gốc.
- (+) Hai module độc lập sau thời điểm mở kỳ thi.
- (−) Nội dung câu hỏi bị nhân bản theo từng kỳ thi.
- (−) Phát hiện đề sai khi đang thi: MVP giữ nguyên kỳ thi. Sau MVP cần thao tác riêng trong `examination` (sửa đáp án và chấm lại, hủy câu), không phải sửa đề gốc.
- Giao diện nên cảnh báo khi giáo viên sửa một đề đang được dùng ở kỳ thi OPEN.
