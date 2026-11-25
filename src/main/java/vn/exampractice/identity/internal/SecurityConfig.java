package vn.exampractice.identity.internal;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.exampractice.shared.security.ProblemDetailAccessDeniedHandler;
import vn.exampractice.shared.security.ProblemDetailAuthenticationEntryPoint;

/**
 * Cấu hình Spring Security cho toàn ứng dụng.
 *
 * <ul>
 *   <li>Stateless: không session, không cookie, nên tắt CSRF; mỗi request tự mang JWT.</li>
 *   <li>CORS: chỉ các địa chỉ giao diện trong {@link CorsProperties} được gọi API từ trình duyệt. Không cho
 *       gửi cookie vì JWT nằm trong header.</li>
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
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
class SecurityConfig {

    private static final String[] SWAGGER = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtService jwtService,
                                            CorsProperties corsProperties,
                                            ProblemDetailAuthenticationEntryPoint entryPoint,
                                            ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsProperties)))
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

    /**
     * Không khai báo địa chỉ nào thì không đặt luật CORS: request thật vẫn chạy như thường, chỉ câu hỏi trước
     * (preflight) của trình duyệt bị từ chối. Nhờ vậy giao diện đi qua proxy vẫn dùng được.
     */
    static CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (properties.allowedOrigins().isEmpty()) {
            return source;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        config.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        config.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** Băm mật khẩu khi đăng ký và so khớp khi đăng nhập. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
