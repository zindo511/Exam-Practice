# ADR-0015: Lấy người đang đăng nhập qua `@AuthenticationPrincipal`, truyền ID xuống service

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-16

## Bối cảnh

Mọi module cần biết ai đang gửi request: `examdesign` cần `teacherId` khi soạn câu hỏi, `examination` cần `studentId` khi học sinh tham gia kỳ thi, `proctoring` cần ID giáo viên đang giám sát. Xác thực dùng JWT không trạng thái; module identity đọc token trong một filter và đặt người dùng vào SecurityContext.

Cần thống nhất hai điều cho mọi module:
- Kiểu đối tượng đặt trong SecurityContext là gì, và nằm ở đâu, khi shared không được phụ thuộc identity và các module khác không được chạm vào `identity.internal`.
- Code nghiệp vụ lấy ID người dùng bằng cách nào. Một số service còn được gọi từ job chạy nền không có request, ví dụ job tự chốt bài hết giờ ([ADR-0013](0013-xu-ly-het-gio-lam-bai.md)).

## Quyết định

- **JWT chứa claim `userId` và `role`.** Filter của identity đọc thẳng từ token, không truy vấn database ở mỗi request.
- **Kiểu principal là `shared.security.AuthenticatedUser(Long id, String role)`.** Identity tạo ra nó trong `JwtAuthenticationFilter`, kèm quyền `ROLE_<role>` để dùng được `hasRole(...)`.
- **Controller nhận người dùng qua `@AuthenticationPrincipal AuthenticatedUser user`** và truyền `user.id()` xuống service như một tham số bình thường.
- **Service không đọc `SecurityContextHolder`.** Mọi dữ liệu về người gọi đều đến qua tham số.

## Phương án đã cân nhắc

- **Tiện ích static (`CurrentUser.id()`) gọi từ service:** controller gọn hơn, nhưng phụ thuộc vào người đăng nhập bị giấu khỏi chữ ký hàm, unit test phải dựng SecurityContext giả, và service hỏng khi được gọi từ job chạy nền vì không có người đăng nhập.
- **Đặt kiểu principal trong `identity.dto`:** controller của mọi module sẽ phụ thuộc identity chỉ để lấy một kiểu hạ tầng xác thực, không phải dữ liệu nghiệp vụ.
- **Dùng `UserDetails` của Spring Security làm principal:** phải tải từ database ở mỗi request, hoặc nhồi ID vào trường `username` dạng chuỗi.
- **Truy vấn database trong filter để lấy thông tin mới nhất:** không cần với MVP, vì vai trò không đổi sau khi đăng ký.

## Hệ quả

- (+) Service là logic thuần: ai gọi cũng dùng được (controller, job, module khác qua `*Api`), unit test chỉ cần truyền một con số.
- (+) Nhìn chữ ký hàm service là biết nó cần ID của ai.
- (+) Không truy vấn database để xác thực mỗi request.
- (−) Mỗi endpoint cần người dùng phải thêm một tham số `@AuthenticationPrincipal`.
- (−) `role` trong `AuthenticatedUser` là chuỗi, không được kiểm tra kiểu như enum `Role` của identity.
- (−) Thông tin trong token có thể cũ cho đến khi token hết hạn. Chấp nhận vì vai trò không đổi trong MVP.
