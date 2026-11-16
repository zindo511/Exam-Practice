package vn.exampractice.identity.internal;

/**
 * Filter đọc header {@code Authorization: Bearer <token>}, kiểm tra token và đặt
 * {@code shared.security.AuthenticatedUser} (lấy từ claim {@code userId}, {@code role}) vào
 * SecurityContext, kèm quyền {@code ROLE_<role>} (ADR-0015). Token sai thì để Security trả 401.
 */
class JwtAuthenticationFilter {
}
