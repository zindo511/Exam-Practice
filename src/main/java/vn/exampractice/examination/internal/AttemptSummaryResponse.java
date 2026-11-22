package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một bài làm trong danh sách kết quả của giáo viên. Chỉ có ID học sinh; tên sẽ lấy qua
 * {@code IdentityApi} khi module identity mở hàm đó (ADR-0007).
 *
 * @param correctCount rỗng khi đang làm
 * @param score        thang 10; rỗng khi đang làm
 */
record AttemptSummaryResponse(
        Long id,
        Long studentId,
        AttemptStatus status,
        Instant startedAt,
        Instant deadline,
        Instant submittedAt,
        int answeredCount,
        int totalQuestions,
        Integer correctCount,
        BigDecimal score) {

    static AttemptSummaryResponse from(Attempt attempt, int totalQuestions) {
        return new AttemptSummaryResponse(attempt.getId(), attempt.getStudentId(), attempt.getStatus(),
                attempt.getStartedAt(), attempt.getDeadline(), attempt.getSubmittedAt(), attempt.getAnswers().size(),
                totalQuestions, attempt.getCorrectCount(), attempt.getScore());
    }
}
