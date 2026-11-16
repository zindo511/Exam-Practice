package vn.exampractice.shared.error;

/**
 * Lỗi nghiệp vụ, luôn mang một {@link ErrorCode}; không bao giờ truyền status khi ném (README 6.8).
 *
 * <p>Là unchecked exception nên {@code @Transactional} tự rollback khi lỗi được ném ra.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** Dùng thông điệp mặc định của mã lỗi làm {@code detail}. */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    /** Thay thông điệp mặc định bằng một câu cụ thể hơn; {@code code} và status không đổi. */
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
