package vn.exampractice.examination.dto;

import java.time.Instant;

/**
 * Tình trạng làm bài của một học sinh trong một kỳ thi, cho màn hình giám sát. Không có điểm: điểm xem ở bảng điểm.
 *
 * <p>Học sinh chưa bắt đầu ({@link AttemptState#NOT_STARTED}) có {@code attemptId}, {@code startedAt},
 * {@code deadline} rỗng và {@code answeredCount = 0}. {@code submittedAt} rỗng khi chưa kết thúc.
 * Thời gian còn lại do bên đọc tự tính: {@code deadline − now}.
 */
public record AttemptSummary(
        Long sessionId,
        Long studentId,
        Long attemptId,
        AttemptState status,
        int answeredCount,
        Instant startedAt,
        Instant deadline,
        Instant submittedAt) {
}
