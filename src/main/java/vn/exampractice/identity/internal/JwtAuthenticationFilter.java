package vn.exampractice.identity.internal;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.exampractice.shared.security.AuthenticatedUser;

/**
 * Đọc header {@code Authorization: Bearer <token>}; token hợp lệ thì đặt
 * {@link AuthenticatedUser} vào SecurityContext kèm quyền {@code ROLE_<role>} (ADR-0015).
 *
 * <p>Thiếu token hoặc token sai: không làm gì, request đi tiếp như chưa đăng nhập. URL công khai
 * vẫn chạy bình thường; URL cần đăng nhập bị Security trả 401 qua
 * {@code ProblemDetailAuthenticationEntryPoint}.
 *
 * <p>Không đánh dấu {@code @Component}: Spring Boot tự đăng ký mọi bean kiểu Filter vào servlet
 * container, khiến filter chạy hai lần. Filter được tạo bằng {@code new} trong {@link SecurityConfig}.
 */
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            jwtService.parse(token).ifPresent(JwtAuthenticationFilter::authenticate);
        }
        chain.doFilter(request, response);
    }

    private static void authenticate(AuthenticatedUser user) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
