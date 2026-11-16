package vn.exampractice.identity.internal;

import org.springframework.http.HttpStatus;
import vn.exampractice.shared.error.ErrorCode;

/**
 * Mã lỗi nghiệp vụ của module identity (docs/error-codes.md, mục identity).
 */
enum IdentityErrorCode implements ErrorCode {

    USERNAME_TAKEN(HttpStatus.CONFLICT, "Tên đăng nhập đã được sử dụng"),

    /**
     * Dùng chung cho "không có username này" và "sai mật khẩu", để không ai dò được username nào
     * đã tồn tại bằng cách thử đăng nhập.
     */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng");

    private final HttpStatus status;
    private final String defaultMessage;

    IdentityErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
