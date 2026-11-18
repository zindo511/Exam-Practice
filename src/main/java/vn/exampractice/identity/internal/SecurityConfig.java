package vn.exampractice.identity.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.exampractice.shared.security.ProblemDetailAccessDeniedHandler;
import vn.exampractice.shared.security.ProblemDetailAuthenticationEntryPoint;

/**
 * Cấu hình Spring Security cho toàn ứng dụng.
 *
 * <ul>
 *   <li>Stateless: không session, không cookie, nên tắt CSRF; mỗi request tự mang JWT.</li>
 *   <li>Phân quyền thô theo tiền tố URL (README 6.2). Quyền theo dữ liệu do từng module tự kiểm
 *       tra.</li>
 *   <li>401/403 trả {@code ProblemDetail} qua hai handler của shared.security (README 6.3).</li>
 *   <li>{@code @EnableMethodSecurity}: khi một tiền tố cho nhiều vai trò (ví dụ {@code /api/exam/**}),
 *       controller tự giới hạn thêm bằng {@code @PreAuthorize}. Lỗi 403 từ đây do
 *       {@code GlobalExceptionHandler} chuyển thành {@code ProblemDetail}.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfig {

    private static final String[] SWAGGER = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtService jwtService,
                                            ProblemDetailAuthenticationEntryPoint entryPoint,
                                            ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(SWAGGER).permitAll()
                        .requestMatchers("/api/design/**").hasRole(Role.TEACHER.name())
                        .requestMatchers("/api/exam/**", "/api/proctor/**")
                        .hasAnyRole(Role.STUDENT.name(), Role.TEACHER.name())
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** Băm mật khẩu khi đăng ký và so khớp khi đăng nhập. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
