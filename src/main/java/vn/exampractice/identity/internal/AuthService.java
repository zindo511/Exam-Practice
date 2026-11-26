package vn.exampractice.identity.internal;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.error.CommonErrorCode;

/**
 * Đăng ký, đăng nhập và người đang đăng nhập.
 */
@Service
class AuthService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    AuthService(UserAccountRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Tạo tài khoản mới với mật khẩu đã băm.
     *
     * <p>Kiểm tra trùng username hai lớp: {@code existsByUsername} để báo lỗi trong trường hợp
     * thường, và bắt lỗi ràng buộc {@code UNIQUE} của database khi hai người đăng ký cùng một
     * username gần như cùng lúc (cả hai cùng qua bước kiểm tra đầu).
     */
    UserResponse register(RegisterRequest request) {
        String username = UserAccount.normalizeUsername(request.username());
        if (repository.existsByUsername(username)) {
            throw new BusinessException(IdentityErrorCode.USERNAME_TAKEN);
        }
        UserAccount user = UserAccount.register(username, passwordEncoder.encode(request.password()),
                request.fullName(), request.role(), Instant.now());
        try {
            repository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(IdentityErrorCode.USERNAME_TAKEN);
        }
        return UserResponse.from(user);
    }

    /** Kiểm tra mật khẩu rồi phát token. Sai username hay sai mật khẩu đều báo cùng một lỗi. */
    @Transactional(readOnly = true)
    TokenResponse login(LoginRequest request) {
        UserAccount user = repository.findByUsername(UserAccount.normalizeUsername(request.username()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS));
        return TokenResponse.bearer(jwtService.issue(user.getId(), user.getRole()), jwtService.expirationSeconds());
    }

    /** Người đang đăng nhập. Token còn hạn mà tài khoản không còn thì coi như chưa đăng nhập. */
    @Transactional(readOnly = true)
    UserResponse currentUser(Long userId) {
        UserAccount user = repository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
        return UserResponse.from(user);
    }
}
