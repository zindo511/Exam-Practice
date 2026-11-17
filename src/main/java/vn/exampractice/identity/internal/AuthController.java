package vn.exampractice.identity.internal;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đăng ký và đăng nhập; công khai, không cần token (README 6.2, 6.3).
 *
 * <p>{@code @SecurityRequirements} rỗng để Swagger UI không đòi token cho hai endpoint này.
 */
@RestController
@SecurityRequirements
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Trả 201 kèm user vừa tạo. Không có header {@code Location} (README 6.4) vì chưa có endpoint
     * nào để xem một user theo ID.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
