package vn.exampractice.identity.internal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.exampractice.shared.security.AuthenticatedUser;

/**
 * Thông tin người dùng; mọi vai trò đã đăng nhập đều gọi được. Không nằm dưới {@code /api/auth/**} vì tiền tố
 * đó công khai (README 6.2).
 */
@RestController
@RequestMapping("/api/users")
class UserController {

    private final AuthService authService;

    UserController(AuthService authService) {
        this.authService = authService;
    }

    /** Người đang đăng nhập. Token chỉ có ID và vai trò nên giao diện gọi API này để lấy tên. */
    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.currentUser(user.id());
    }
}
