# ADR-0009: Response API: DTO trực tiếp, `PageResponse`, ProblemDetail + `ErrorCode`

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15

## Bối cảnh

Frontend cần một định dạng response thống nhất để xử lý thành công và lỗi theo cùng một cách. Kiểu phổ biến là bọc mọi response trong `ApiResponse { success, code, message, data }`.

## Quyết định

**Thành công:** trả thẳng DTO; ý nghĩa nằm ở HTTP status (`200`, `201` + `Location`, `204`).

**Danh sách phân trang:** `PageResponse<T>(content, page, size, totalElements, totalPages)` trong `shared`, tạo từ `Page` của Spring Data. Không trả thẳng `Page` ra API. `page` bắt đầu từ 0, `size` mặc định 20, tối đa 100. Danh sách có giới hạn tự nhiên (câu hỏi của một đề, học sinh của một kỳ thi) trả mảng thường.

**Lỗi:** chuẩn **ProblemDetail** (RFC 9457), `Content-Type: application/problem+json`, thêm các field mở rộng:
- `code`: mã lỗi máy đọc được, frontend xử lý theo field này.
- `timestamp`: để đối chiếu log.
- `errors`: danh sách lỗi theo field, chỉ có ở lỗi validation.

**Mã lỗi là enum mang sẵn status:**
- Interface `ErrorCode` trong `shared`; mỗi module có enum riêng trong `internal` (ví dụ `ExaminationErrorCode`).
- Mỗi hằng mang `status` và thông điệp mặc định; **status được lấy từ code**, một code luôn đi với đúng một status.
- `BusinessException` chỉ nhận `ErrorCode` (và `detail` tùy chọn), **không nhận status**.
- `GlobalExceptionHandler` (kế thừa `ResponseEntityExceptionHandler`) là nơi duy nhất chuyển exception thành ProblemDetail. Lỗi `401`/`403` của Spring Security được xử lý riêng bằng `AuthenticationEntryPoint` và `AccessDeniedHandler`.

## Phương án đã cân nhắc

- **Bọc mọi response trong `ApiResponse`:** lặp lại thông tin của HTTP status; dễ dẫn tới trả `200` kèm `success: false`, khiến log, giám sát và retry hiểu sai; tài liệu OpenAPI khó đọc. Những lợi ích mong muốn (mã lỗi, thông điệp thống nhất) đều nằm ở phía lỗi, và ProblemDetail đã giải quyết.
- **Định dạng lỗi tự nghĩ ra:** không có lý do để không dùng chuẩn mà Spring hỗ trợ sẵn.
- **Truyền status mỗi lần ném lỗi:** sớm muộn cùng một code sẽ trả hai status khác nhau.
- **Dùng `PagedModel` của Spring Data:** không cần tự viết, nhưng định dạng do Spring quyết định.

## Hệ quả

- (+) HTTP status luôn đúng; công cụ bên ngoài hiểu đúng response.
- (+) Frontend xử lý lỗi theo `code`, không so sánh chuỗi thông điệp.
- (+) Ánh xạ code → status chỉ khai báo ở một nơi; không thể ném lỗi sai status.
- (+) Spring tự điền `type`, `title`, `instance`, content type.
- (−) Frontend phải xử lý hai dạng body: DTO khi thành công, ProblemDetail khi lỗi (phân biệt bằng status).
- Danh sách mã lỗi: [docs/error-codes.md](../error-codes.md).
