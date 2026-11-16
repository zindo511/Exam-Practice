package vn.exampractice.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Trả 401 {@code UNAUTHENTICATED} dạng {@code ProblemDetail} khi thiếu token, token sai hoặc
 * hết hạn.
 *
 * <p>Lỗi này xảy ra trong filter của Security, trước controller, nên không tự đi vào
 * GlobalExceptionHandler. Thay vì tự ghi JSON, class này chuyển exception cho
 * {@link HandlerExceptionResolver} của Spring MVC, để GlobalExceptionHandler xử lý như mọi lỗi
 * khác: cùng định dạng, cùng cách ghi JSON, chỉ một nơi chuyển exception thành response
 * (README 6.8).
 *
 * <p>Đăng ký trong cấu hình Security: {@code exceptionHandling(e -> e.authenticationEntryPoint(...))}.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    public ProblemDetailAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) {
        resolver.resolveException(request, response, null, authException);
    }
}
