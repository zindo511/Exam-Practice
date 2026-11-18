package vn.exampractice.examination.internal;

import org.springframework.http.HttpStatus;
import vn.exampractice.shared.error.ErrorCode;

/**
 * Mã lỗi nghiệp vụ của module examination (docs/error-codes.md, mục examination). Các mã của phần
 * bài làm được thêm khi viết phần đó.
 */
enum ExaminationErrorCode implements ErrorCode {

    /** Kỳ thi không tồn tại hoặc thuộc giáo viên khác; gộp chung để không lộ ID nào đang tồn tại. */
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ thi"),

    /** Thao tác không hợp lệ với trạng thái hiện tại của kỳ thi, ví dụ hủy kỳ thi đã mở. */
    INVALID_SESSION_STATE(HttpStatus.CONFLICT, "Thao tác không hợp lệ với trạng thái hiện tại của kỳ thi"),

    /** Đề không tồn tại, không phải của giáo viên, đã lưu trữ hoặc không có câu nào. */
    EXAM_NOT_AVAILABLE(HttpStatus.CONFLICT, "Đề không dùng được"),

    /**
     * Mã sai, kỳ thi đã kết thúc, hoặc giáo viên đã khóa tham gia. Gộp chung để không lộ mã nào
     * đang tồn tại (ADR-0010).
     */
    INVALID_ACCESS_CODE(HttpStatus.CONFLICT, "Mã kỳ thi không đúng hoặc kỳ thi không nhận tham gia"),

    /** Nhập sai mã quá 5 lần trong 15 phút; tạm chặn nhập mã 15 phút (ADR-0010). */
    ACCESS_CODE_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Nhập sai mã quá nhiều lần, vui lòng thử lại sau");

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
