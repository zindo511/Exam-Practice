# ADR-0002: Chia 4 module: identity, examdesign, examination, proctoring

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15

## Bối cảnh

Domain cốt lõi là **tổ chức và giám sát một buổi thi**. Danh sách ban đầu có 6–7 phần (ngân hàng câu hỏi, soạn đề, kỳ thi, lượt làm bài, chấm điểm, giám sát, tài khoản), nhưng ở MVP nhiều phần quá mỏng và luôn thay đổi cùng nhau.

Tiêu chí tách: khác mô hình hoặc ngôn ngữ, khác tốc độ thay đổi, khác yêu cầu nhất quán. Tiêu chí gộp: luôn đổi cùng nhau, cần chung một transaction, tách ra chỉ thêm thủ tục.

## Quyết định

| Module | Loại | Chứa |
|---|---|---|
| `identity` | Generic | Tài khoản, vai trò, đăng nhập |
| `examdesign` | Supporting | Câu hỏi, đề thi |
| `examination` | Core | Kỳ thi, tham gia, lượt làm bài, đáp án, tự chấm |
| `proctoring` | Core | Heartbeat, sự kiện bất thường, bảng giám sát |

- **Ngân hàng câu hỏi + soạn đề → `examdesign`:** cùng người dùng (giáo viên), cùng giai đoạn soạn thảo.
- **Kỳ thi nằm trong `examination`, không nằm trong `examdesign`:** luật của kỳ thi (giờ mở, ai được thi) là thứ lượt làm bài kiểm tra liên tục.
- **Chấm điểm gộp vào `examination`:** chấm trắc nghiệm là một bước của việc nộp bài, cần cùng transaction.
- **`proctoring` tách riêng:** mô hình khác (chỉ đọc, tổng hợp), tải khác (heartbeat liên tục), công nghệ sẽ đổi (polling → SSE/WebSocket).
- Tên `examdesign` được chọn thay cho `authoring` vì "authoring" dễ nhầm với "authorization".

`examdesign` lo **nội dung đề**; `examination` lo **việc thi thật bằng đề đó**. Điểm nối duy nhất là lúc mở kỳ thi ([ADR-0003](0003-snapshot-de-khi-mo-ky-thi.md)).

## Phương án đã cân nhắc

- **Chia mịn 6–7 module ngay từ đầu:** nhiều interface chưa ai cần, nhiều module chỉ có vài class.
- **Gộp `proctoring` vào `examination`:** heartbeat và màn hình giám sát sẽ chạm vào bảng bài làm; mỗi lần đổi cách giám sát có nguy cơ làm hỏng luồng làm bài.

## Hệ quả

- (+) Mỗi module đủ lớn để có ý nghĩa, đủ nhỏ để một người nắm được.
- (+) Phần cốt lõi (làm bài) tách khỏi phần hay thay đổi (giám sát).
- (−) `examination` là module lớn nhất; cần theo dõi kích thước.
- Tách thêm khi: có tự luận hoặc chấm tay (`grading`), cần lớp học và danh sách thí sinh (`classroom`), ngân hàng câu hỏi dùng chung và sinh đề ngẫu nhiên (tách `questionbank`), thống kê nâng cao (`analytics`).
