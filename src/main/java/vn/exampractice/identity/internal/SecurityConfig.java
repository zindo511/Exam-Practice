package vn.exampractice.identity.internal;

/**
 * Cấu hình Spring Security: stateless, mở {@code /api/auth/**}, gắn JwtAuthenticationFilter,
 * mã hóa mật khẩu bằng BCrypt và đăng ký hai handler 401/403 của shared.security.
 */
class SecurityConfig {
}
