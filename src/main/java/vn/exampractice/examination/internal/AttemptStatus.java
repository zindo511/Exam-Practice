package vn.exampractice.examination.internal;

/** Trạng thái bài làm (state-machines.md mục 2). Ba trạng thái kết thúc đều đã được chấm điểm. */
enum AttemptStatus {

    /** Đang làm; lưu đáp án và nộp được tới hạn chót cộng 5 giây ân hạn. */
    IN_PROGRESS,

    /** Học sinh tự nộp. */
    SUBMITTED,

    /** Hết giờ, hoặc kỳ thi đóng khi bài còn đang làm. */
    EXPIRED,

    /** Giáo viên khóa bài vì vi phạm. */
    LOCKED;

    boolean isFinished() {
        return this != IN_PROGRESS;
    }
}
