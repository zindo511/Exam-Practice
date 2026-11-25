package vn.exampractice.examination.internal;

/**
 * Bộ lọc trạng thái trên bảng điểm của giáo viên ({@code ?status=}). Khác {@link AttemptStatus} ở chỗ
 * có thêm {@link #NOT_STARTED}: học sinh đã vào phòng chờ nhưng chưa có bài làm.
 */
enum ResultFilter {

    NOT_STARTED(null),
    IN_PROGRESS(AttemptStatus.IN_PROGRESS),
    SUBMITTED(AttemptStatus.SUBMITTED),
    EXPIRED(AttemptStatus.EXPIRED),
    LOCKED(AttemptStatus.LOCKED);

    private final AttemptStatus attemptStatus;

    ResultFilter(AttemptStatus attemptStatus) {
        this.attemptStatus = attemptStatus;
    }

    /** Trạng thái bài làm tương ứng; rỗng với {@link #NOT_STARTED}. */
    AttemptStatus attemptStatus() {
        return attemptStatus;
    }
}
