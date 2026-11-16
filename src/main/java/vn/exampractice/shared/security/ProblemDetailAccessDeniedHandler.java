package vn.exampractice.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Trả 403 {@code FORBIDDEN} dạng {@code ProblemDetail} khi đã đăng nhập nhưng không đủ quyền.
 *
 * <p>Giống {@link ProblemDetailAuthenticationEntryPoint}: chuyển exception cho
 * GlobalExceptionHandler thay vì tự ghi JSON.
 *
 * <p>Đăng ký trong cấu hình Security: {@code exceptionHandling(e -> e.accessDeniedHandler(...))}.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public ProblemDetailAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null, accessDeniedException);
    }
}
