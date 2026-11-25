package vn.exampractice.proctoring.internal;

import org.springframework.http.HttpStatus;
import vn.exampractice.shared.error.ErrorCode;

/**
 * Mã lỗi nghiệp vụ của module proctoring (docs/error-codes.md, mục proctoring).
 */
enum ProctoringErrorCode implements ErrorCode {

    /**
     * Bài làm không tồn tại hoặc của học sinh khác; gộp chung để không lộ ID nào đang tồn tại. Cùng
     * {@code code} và status với mã của examination vì cùng ý nghĩa, client chỉ cần xử lý một mã.
     */
    ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài làm");

    private final HttpStatus status;
    private final String defaultMessage;

    ProctoringErrorCode(HttpStatus status, String defaultMessage) {
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
