package vn.exampractice.support;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.exampractice.shared.security.AuthenticatedUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Giả lập người đang đăng nhập trong test MockMvc của các module, đúng như
 * {@code JwtAuthenticationFilter} đặt vào SecurityContext (ADR-0015): principal là
 * {@link AuthenticatedUser}, quyền là {@code ROLE_<role>}.
 *
 * <p>Module khác không gọi được {@code JwtService} của identity (package-private), nên dùng tiện ích
 * này thay vì phát token thật. Luồng JWT thật đã được kiểm tra trong test của identity.
 */
public final class TestAuth {

    /** Secret JWT chỉ dùng cho test, để chạy được trên máy không có file .env. */
    public static final String JWT_SECRET_PROPERTY =
            "app.jwt.secret=khoa-bi-mat-chi-dung-cho-test-dai-hon-32-byte-0123456789";

    private TestAuth() {
    }

    public static RequestPostProcessor teacher(long id) {
        return as(id, "TEACHER");
    }

    public static RequestPostProcessor student(long id) {
        return as(id, "STUDENT");
    }

    private static RequestPostProcessor as(long id, String role) {
        AuthenticatedUser user = new AuthenticatedUser(id, role);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
