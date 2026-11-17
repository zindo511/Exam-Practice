package vn.exampractice.examdesign.internal;

import org.springframework.http.HttpStatus;
import vn.exampractice.shared.error.ErrorCode;

/**
 * Mã lỗi nghiệp vụ của module examdesign (docs/error-codes.md, mục examdesign).
 * Các mã của đề thi được thêm khi viết phần đề.
 */
enum ExamDesignErrorCode implements ErrorCode {

    /**
     * Câu hỏi không tồn tại, đã lưu trữ, hoặc thuộc giáo viên khác. Gộp chung để không lộ ID nào
     * đang tồn tại.
     */
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi");

    private final HttpStatus status;
    private final String defaultMessage;

    ExamDesignErrorCode(HttpStatus status, String defaultMessage) {
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
