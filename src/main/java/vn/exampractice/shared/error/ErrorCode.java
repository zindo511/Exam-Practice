package vn.exampractice.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi máy đọc được: {@code code}, HTTP status cố định và thông điệp mặc định (tiếng Việt).
 * Mỗi module có enum riêng trong {@code internal} implement interface này (ADR-0009).
 *
 * <p>Một {@code code} luôn đi kèm một status cố định (README 6.7), nên status nằm trong mã lỗi
 * chứ không truyền lúc ném exception.
 */
public interface ErrorCode {

    /** Giá trị field {@code code} trong ProblemDetail, dạng UPPER_SNAKE_CASE. */
    String code();

    HttpStatus status();

    /** Thông điệp tiếng Việt dùng làm {@code detail} khi không có câu cụ thể hơn. */
    String defaultMessage();
}
