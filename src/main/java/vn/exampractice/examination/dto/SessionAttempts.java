package vn.exampractice.examination.dto;

import java.time.Instant;
import java.util.List;

/**
 * Một kỳ thi và tình trạng làm bài của mọi học sinh đã tham gia, theo thứ tự vào phòng chờ.
 *
 * @param totalQuestions số câu của đề đã chụp; 0 khi kỳ thi chưa mở
 */
public record SessionAttempts(
        Long sessionId,
        String title,
        SessionState status,
        Instant startAt,
        Instant endAt,
        int totalQuestions,
        List<AttemptSummary> attempts) {

    public SessionAttempts {
        attempts = List.copyOf(attempts);
    }
}
