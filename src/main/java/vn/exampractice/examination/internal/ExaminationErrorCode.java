package vn.exampractice.examination.internal;

import org.springframework.http.HttpStatus;
import vn.exampractice.shared.error.ErrorCode;

/**
 * Mã lỗi nghiệp vụ của module examination (docs/error-codes.md, mục examination). Các mã của phần
 * tham gia và bài làm được thêm khi viết các phần đó.
 */
enum ExaminationErrorCode implements ErrorCode {

    /** Kỳ thi không tồn tại hoặc thuộc giáo viên khác; gộp chung để không lộ ID nào đang tồn tại. */
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ thi"),

    /** Thao tác không hợp lệ với trạng thái hiện tại của kỳ thi, ví dụ hủy kỳ thi đã mở. */
    INVALID_SESSION_STATE(HttpStatus.CONFLICT, "Thao tác không hợp lệ với trạng thái hiện tại của kỳ thi"),

    /** Đề không tồn tại, không phải của giáo viên, đã lưu trữ hoặc không có câu nào. */
    EXAM_NOT_AVAILABLE(HttpStatus.CONFLICT, "Đề không dùng được");

    private final HttpStatus status;
    private final String defaultMessage;

    ExaminationErrorCode(HttpStatus status, String defaultMessage) {
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
