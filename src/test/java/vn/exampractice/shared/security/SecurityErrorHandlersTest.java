package vn.exampractice.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra 401 và 403 do filter của Spring Security trả về có cùng định dạng
 * {@code ProblemDetail} với mọi lỗi khác (README 6.3, 6.6).
 *
 * <p>Cấu hình Security thật sẽ nằm ở module identity; ở đây dùng một cấu hình giả chỉ có
 * trong test: {@code /fake/teacher} cần vai trò TEACHER, mọi URL khác chỉ cần đăng nhập.
 */
@WebMvcTest(controllers = SecurityErrorHandlersTest.FakeController.class)
@Import({ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class,
        SecurityErrorHandlersTest.FakeController.class})
class SecurityErrorHandlersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void request_without_authentication_gets_401_problem_detail() throws Exception {
        mockMvc.perform(get("/fake/student"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.instance").value("/fake/student"))
                .andExpect(jsonPath("$.detail", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void authenticated_user_without_required_role_gets_403_problem_detail() throws Exception {
        mockMvc.perform(get("/fake/teacher").with(user("hoc-sinh").roles("STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.instance").value("/fake/teacher"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void user_with_required_role_passes() throws Exception {
        mockMvc.perform(get("/fake/teacher").with(user("giao-vien").roles("TEACHER")))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class FakeSecurityConfig {

        @Bean
        SecurityFilterChain fakeSecurityFilterChain(HttpSecurity http,
                                                    ProblemDetailAuthenticationEntryPoint entryPoint,
                                                    ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
            return http
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/fake/teacher").hasRole("TEACHER")
                            .anyRequest().authenticated())
                    .exceptionHandling(e -> e
                            .authenticationEntryPoint(entryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .build();
        }
    }

    @RestController
    static class FakeController {

        @GetMapping("/fake/student")
        void student() {
        }

        @GetMapping("/fake/teacher")
        void teacher() {
        }
    }
}
