package vn.exampractice.shared.security;

/**
 * Trả 401 {@code UNAUTHENTICATED} dạng {@code ProblemDetail} khi thiếu token, token sai hoặc
 * hết hạn. Lỗi này xảy ra trước controller nên không đi qua GlobalExceptionHandler (README 6.8).
 */
public class ProblemDetailAuthenticationEntryPoint {
}
