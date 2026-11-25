package vn.exampractice.identity.internal;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.exampractice.shared.security.AuthenticatedUser;
import vn.exampractice.shared.security.ProblemDetailAccessDeniedHandler;
import vn.exampractice.shared.security.ProblemDetailAuthenticationEntryPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra cấu hình Security thật của ứng dụng với JWT thật (README 6.2, 6.3, ADR-0015):
 * URL công khai, 401 khi thiếu hoặc sai token, phân quyền theo tiền tố, CORS, và controller nhận được
 * {@link AuthenticatedUser} qua {@code @AuthenticationPrincipal}.
 */
@WebMvcTest(controllers = SecurityConfigTest.FakeController.class,
        properties = {"app.jwt.secret=khoa-bi-mat-chi-dung-cho-test-dai-hon-32-byte-0123456789",
                "app.cors.allowed-origins=http://localhost:5173, https://thi.example.com"})
@Import({SecurityConfig.class, JwtService.class,
        ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class,
        SecurityConfigTest.FakeController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void auth_endpoints_are_public() throws Exception {
        mockMvc.perform(get("/api/auth/ping"))
                .andExpect(status().isOk());
    }

    @Test
    void protected_endpoint_without_token_is_401() throws Exception {
        expectUnauthenticated(mockMvc.perform(get("/api/exam/me")));
    }

    @Test
    void invalid_token_is_401() throws Exception {
        expectUnauthenticated(mockMvc.perform(get("/api/exam/me").header(HttpHeaders.AUTHORIZATION, "Bearer sai")));
    }

    @Test
    void non_bearer_authorization_header_is_ignored() throws Exception {
        String token = jwtService.issue(1L, Role.STUDENT);

        expectUnauthenticated(mockMvc.perform(get("/api/exam/me").header(HttpHeaders.AUTHORIZATION, "Basic " + token)));
    }

    @Test
    void controller_receives_authenticated_user_from_token() throws Exception {
        mockMvc.perform(get("/api/exam/me").header(HttpHeaders.AUTHORIZATION, bearer(7L, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void student_cannot_call_design_endpoints() throws Exception {
        mockMvc.perform(get("/api/design/ping").header(HttpHeaders.AUTHORIZATION, bearer(7L, Role.STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void teacher_can_call_design_endpoints() throws Exception {
        mockMvc.perform(get("/api/design/ping").header(HttpHeaders.AUTHORIZATION, bearer(9L, Role.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(content().string("9"));
    }

    /** Trình duyệt hỏi trước bằng OPTIONS không kèm token; phải được trả lời chứ không phải 401. */
    @Test
    void preflight_from_allowed_origin_needs_no_token() throws Exception {
        for (String origin : List.of("http://localhost:5173", "https://thi.example.com")) {
            mockMvc.perform(options("/api/exam/me")
                            .header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                            "authorization, content-type"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }
    }

    @Test
    void preflight_from_other_origin_is_refused() throws Exception {
        mockMvc.perform(options("/api/exam/me")
                        .header(HttpHeaders.ORIGIN, "https://la.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void allowed_origin_can_read_response_and_location() throws Exception {
        mockMvc.perform(get("/api/exam/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.AUTHORIZATION, bearer(7L, Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.LOCATION));
    }

    /** Không có header CORS trên lỗi 401 thì giao diện không đọc được {@code code} để chuyển về trang đăng nhập. */
    @Test
    void allowed_origin_can_read_unauthenticated_error() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/exam/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173"));

        expectUnauthenticated(result);
        result.andExpect(
                header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void no_configured_origin_means_no_cors_rule() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/exam/me");

        assertThat(SecurityConfig.corsConfigurationSource(new CorsProperties(null))
                .getCorsConfiguration(request)).isNull();
        assertThat(SecurityConfig.corsConfigurationSource(new CorsProperties(List.of()))
                .getCorsConfiguration(request)).isNull();
    }

    private String bearer(Long userId, Role role) {
        return "Bearer " + jwtService.issue(userId, role);
    }

    private static void expectUnauthenticated(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @RestController
    static class FakeController {

        @GetMapping("/api/auth/ping")
        String publicPing() {
            return "ok";
        }

        @GetMapping("/api/exam/me")
        AuthenticatedUser me(@AuthenticationPrincipal AuthenticatedUser user) {
            return user;
        }

        @GetMapping("/api/design/ping")
        Long teacherOnly(@AuthenticationPrincipal AuthenticatedUser user) {
            return user.id();
        }
    }
}
