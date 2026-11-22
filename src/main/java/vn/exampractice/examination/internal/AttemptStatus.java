package vn.exampractice.examination.internal;

/**
 * Trạng thái của bài làm (state-machines.md mục 2). Cả ba trạng thái kết thúc đều có điểm; trạng
 * thái chỉ cho biết bài kết thúc bằng cách nào.
 */
enum AttemptStatus {

    /** Đang làm; còn lưu được đáp án tới hạn chót cộng ân hạn. */
    IN_PROGRESS,

    /** Học sinh tự nộp, hoặc giáo viên buộc nộp. */
    SUBMITTED,

    /** Hết giờ mà chưa nộp, hoặc kỳ thi đóng khi đang làm; server tự chốt (ADR-0013). */
    EXPIRED,

    /** Giáo viên khóa bài để xem xét (ADR-0012). */
    LOCKED;

    boolean isFinished() {
        return this != IN_PROGRESS;
    }
}
