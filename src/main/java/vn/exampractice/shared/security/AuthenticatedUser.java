package vn.exampractice.shared.security;

/**
 * Người dùng đang đăng nhập, đặt trong SecurityContext cho mỗi request có JWT hợp lệ
 * (ADR-0015).
 *
 * <p>Module identity tạo ra đối tượng này (filter đọc claim của JWT); controller của mọi module
 * nhận nó qua {@code @AuthenticationPrincipal AuthenticatedUser user} rồi truyền
 * {@code user.id()} xuống service. Service không tự đọc SecurityContext.
 *
 * <p>Nằm ở shared vì mọi module đều dùng, trong khi shared không được phụ thuộc identity; vì
 * vậy {@code role} là chuỗi ({@code STUDENT}, {@code TEACHER}) thay vì enum của identity.
 *
 * @param id   ID trong bảng {@code identity_user}
 * @param role vai trò, không có tiền tố {@code ROLE_}
 */
public record AuthenticatedUser(Long id, String role) {
}
