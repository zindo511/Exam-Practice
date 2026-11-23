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

    /**
     * Mã sai, kỳ thi đã kết thúc, hoặc giáo viên đã khóa tham gia. Gộp chung để không lộ mã nào
     * đang tồn tại (ADR-0010).
     */
    INVALID_ACCESS_CODE(HttpStatus.CONFLICT, "Mã kỳ thi không đúng hoặc kỳ thi không nhận tham gia"),

    /** Nhập sai mã quá 5 lần trong 15 phút; tạm chặn nhập mã 15 phút (ADR-0010). */
    ACCESS_CODE_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Nhập sai mã quá nhiều lần, vui lòng thử lại sau"),

    /** Kỳ thi chưa mở hoặc đã đóng khi bắt đầu làm bài. */
    SESSION_NOT_OPEN(HttpStatus.CONFLICT, "Kỳ thi chưa mở hoặc đã đóng"),

    /** Bắt đầu làm bài khi chưa nhập mã tham gia kỳ thi. */
    NOT_A_PARTICIPANT(HttpStatus.CONFLICT, "Bạn chưa tham gia kỳ thi này"),

    /** Học sinh đã có bài làm trong kỳ thi (mỗi kỳ thi một lượt, ADR-0010). */
    ATTEMPT_ALREADY_STARTED(HttpStatus.CONFLICT, "Bạn đã bắt đầu làm bài trong kỳ thi này"),

    /** Bài làm không tồn tại hoặc không thuộc về người gọi. */
    ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài làm"),

    /** Bài đã hết giờ (quá hạn chót cộng 5 giây ân hạn, hoặc đã bị chốt). */
    ATTEMPT_EXPIRED(HttpStatus.CONFLICT, "Bài thi đã hết giờ"),

    ATTEMPT_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Bài thi đã nộp"),

    ATTEMPT_LOCKED(HttpStatus.CONFLICT, "Bài thi bị giáo viên khóa"),

    /** Lưu đáp án cho một câu không thuộc bản chụp đề của kỳ thi. */
    SESSION_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Câu hỏi không thuộc bài thi này");

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
