package vn.exampractice.examination.internal;

import org.springframework.http.HttpStatus;
import vn.exampractice.shared.error.ErrorCode;

/**
 * Mã lỗi nghiệp vụ của module examination (docs/error-codes.md, mục examination).
 */
enum ExaminationErrorCode implements ErrorCode {

    /** Kỳ thi không tồn tại hoặc thuộc giáo viên khác; gộp chung để không lộ ID nào đang tồn tại. */
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ thi"),

    /** Thao tác không hợp lệ với trạng thái hiện tại của kỳ thi, ví dụ hủy kỳ thi đã mở. */
    INVALID_SESSION_STATE(HttpStatus.CONFLICT, "Thao tác không hợp lệ với trạng thái hiện tại của kỳ thi"),

    /** Đề không tồn tại, không phải của giáo viên, đã lưu trữ hoặc không có câu nào. */
    EXAM_NOT_AVAILABLE(HttpStatus.CONFLICT, "Đề không dùng được"),

    /** Bài làm không tồn tại hoặc không thuộc về người gọi; gộp chung để không lộ ID nào đang tồn tại. */
    ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài làm"),

    /** Mã sai, kỳ thi đã đóng hoặc hủy, hoặc đã khóa tham gia; gộp chung để không giúp dò mã. */
    INVALID_ACCESS_CODE(HttpStatus.CONFLICT, "Mã kỳ thi không đúng hoặc đã hết hiệu lực"),

    /** Kỳ thi chưa mở hoặc đã đóng. */
    SESSION_NOT_OPEN(HttpStatus.CONFLICT, "Kỳ thi chưa mở hoặc đã đóng"),

    /** Bắt đầu làm bài khi chưa nhập mã tham gia kỳ thi. */
    NOT_A_PARTICIPANT(HttpStatus.CONFLICT, "Bạn chưa tham gia kỳ thi này"),

    /** Mỗi học sinh một lượt làm bài cho mỗi kỳ thi (ADR-0010). */
    ATTEMPT_ALREADY_STARTED(HttpStatus.CONFLICT, "Bạn đã bắt đầu làm bài trong kỳ thi này"),

    ATTEMPT_EXPIRED(HttpStatus.CONFLICT, "Bài thi đã hết giờ"),

    ATTEMPT_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Bài thi đã nộp"),

    ATTEMPT_LOCKED(HttpStatus.CONFLICT, "Bài thi đã bị giáo viên khóa");

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
