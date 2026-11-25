# Bảng mã lỗi

Danh sách các giá trị `code` trong response lỗi `ProblemDetail`. Định dạng và cách dùng xem [README, mục 6.6](../README.md#66-response-lỗi-problemdetail-rfc-9457).

Khi thêm một mã lỗi mới, **bổ sung vào file này** trong cùng commit.

## Mã chung

| `code` | Status | Khi nào |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Dữ liệu đầu vào không hợp lệ (kèm `errors`) |
| `MALFORMED_REQUEST` | 400 | Body không đọc được, sai kiểu tham số, thiếu tham số bắt buộc, `sort` theo field không tồn tại |
| `UNAUTHENTICATED` | 401 | Thiếu token, token sai hoặc hết hạn |
| `FORBIDDEN` | 403 | Không đủ quyền |
| `NOT_FOUND` | 404 | Không tìm thấy tài nguyên hoặc endpoint |
| `METHOD_NOT_ALLOWED` | 405 | Sai HTTP method |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` của request không được hỗ trợ (ví dụ gửi `text/plain` cho endpoint nhận JSON) |
| `CONCURRENT_MODIFICATION` | 409 | Dữ liệu vừa bị thao tác khác cập nhật (optimistic locking), cần tải lại rồi thử lại |
| `INTERNAL_ERROR` | 500 | Lỗi không lường trước (chi tiết chỉ ghi trong log server) |

## Mã nghiệp vụ

Status `404` cho "không tìm thấy", `409` cho vi phạm luật hoặc sai trạng thái, `429` khi vượt giới hạn số lần thử.

### identity

| `code` | Status | Khi nào |
|---|---|---|
| `USERNAME_TAKEN` | 409 | Đăng ký với tên đăng nhập đã có (không phân biệt hoa thường) |
| `INVALID_CREDENTIALS` | 401 | Đăng nhập sai tên đăng nhập hoặc mật khẩu; không nói rõ sai phần nào để không lộ username nào đã tồn tại |

### examdesign

| `code` | Status | Khi nào |
|---|---|---|
| `QUESTION_NOT_FOUND` | 404 | Câu hỏi không tồn tại, đã lưu trữ, hoặc thuộc giáo viên khác |
| `EXAM_NOT_FOUND` | 404 | Đề không tồn tại, đã lưu trữ, hoặc thuộc giáo viên khác |
| `QUESTION_NOT_AVAILABLE` | 409 | Thay danh sách câu của đề bằng câu không tồn tại, đã lưu trữ hoặc của giáo viên khác; `detail` liệt kê ID các câu đó |
| `QUESTION_IN_USE` | 409 | Lưu trữ câu hỏi đang nằm trong một đề chưa lưu trữ; `detail` liệt kê tên các đề đó |

### examination

| `code` | Status | Khi nào |
|---|---|---|
| `SESSION_NOT_FOUND` | 404 | Kỳ thi không tồn tại hoặc thuộc giáo viên khác |
| `ATTEMPT_NOT_FOUND` | 404 | Bài làm không tồn tại hoặc không thuộc về người gọi |
| `INVALID_ACCESS_CODE` | 409 | Mã kỳ thi sai, kỳ thi đã kết thúc, hoặc đã khóa tham gia; chỉ mã sai mới tính vào giới hạn nhập sai |
| `ACCESS_CODE_ATTEMPTS_EXCEEDED` | 429 | Nhập sai mã quá 5 lần trong 15 phút; tạm chặn nhập mã 15 phút |
| `SESSION_NOT_OPEN` | 409 | Kỳ thi chưa mở hoặc đã đóng |
| `INVALID_SESSION_STATE` | 409 | Thao tác không hợp lệ với trạng thái hiện tại của kỳ thi (ví dụ hủy kỳ thi đã mở) |
| `EXAM_NOT_AVAILABLE` | 409 | Đề không tồn tại, không phải của giáo viên, đã lưu trữ hoặc không có câu nào, khi tạo hoặc mở kỳ thi |
| `NOT_A_PARTICIPANT` | 409 | Bắt đầu làm bài khi chưa nhập mã tham gia kỳ thi |
| `ATTEMPT_ALREADY_STARTED` | 409 | Học sinh đã có bài làm trong kỳ thi này |
| `ATTEMPT_EXPIRED` | 409 | Bài thi đã hết giờ |
| `ATTEMPT_ALREADY_SUBMITTED` | 409 | Bài thi đã nộp |
| `ATTEMPT_LOCKED` | 409 | Bài thi bị giáo viên khóa |
| `SESSION_QUESTION_NOT_FOUND` | 404 | Lưu đáp án cho câu không thuộc bản chụp đề của kỳ thi |

### proctoring

| `code` | Status | Khi nào |
|---|---|---|
| `ATTEMPT_NOT_FOUND` | 404 | Gửi tín hiệu giám sát cho bài làm không tồn tại hoặc của học sinh khác; cùng mã và status với `examination` vì cùng ý nghĩa |
