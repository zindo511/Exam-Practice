package vn.exampractice.shared.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tài liệu OpenAPI cho Swagger UI ({@code /swagger-ui.html}).
 *
 * <p>Khai báo scheme {@value #BEARER_AUTH} (HTTP Bearer, JWT) và đặt làm mặc định cho mọi endpoint,
 * để Swagger UI có nút <b>Authorize</b>: dán {@code accessToken} lấy từ {@code POST /api/auth/login}
 * một lần, các request sau tự gửi kèm {@code Authorization: Bearer <token>}. Endpoint công khai bỏ
 * yêu cầu này bằng {@code @SecurityRequirements} rỗng (ví dụ {@code AuthController}).
 *
 * <p>Chỉ ảnh hưởng tài liệu; quyền truy cập thật do {@code SecurityConfig} quyết định.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Exam-Practice API", version = "v1"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Dán accessToken lấy từ POST /api/auth/login, không gõ chữ \"Bearer\"")
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
