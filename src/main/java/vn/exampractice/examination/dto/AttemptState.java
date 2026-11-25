package vn.exampractice.examination.dto;

/**
 * Trạng thái bài làm của một học sinh, nhìn từ bên ngoài examination (state-machines.md mục 2), thêm
 * {@link #NOT_STARTED} cho học sinh đã tham gia mà chưa bắt đầu (tính lúc đọc, mục 4).
 */
public enum AttemptState {

    /** Đã nhập mã tham gia, chưa bấm bắt đầu; chưa có bài làm. */
    NOT_STARTED,

    /** Đang làm. */
    IN_PROGRESS,

    /** Học sinh tự nộp. */
    SUBMITTED,

    /** Hết giờ, hoặc kỳ thi đóng khi bài còn đang làm. */
    EXPIRED,

    /** Giáo viên khóa bài vì vi phạm. */
    LOCKED
}
