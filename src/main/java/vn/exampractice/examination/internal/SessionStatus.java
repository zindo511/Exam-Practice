package vn.exampractice.examination.internal;

/** Trạng thái của kỳ thi (state-machines.md mục 1). */
enum SessionStatus {

    /** Đã tạo, chưa mở; học sinh nhập mã được nhưng chưa làm bài được. */
    SCHEDULED,

    /** Đang thi; đã chụp snapshot đề. */
    OPEN,

    /** Đã kết thúc. */
    CLOSED,

    /** Giáo viên hủy trước khi mở. */
    CANCELLED
}
