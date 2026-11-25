package vn.exampractice.proctoring.internal;

import java.time.Instant;

import vn.exampractice.examination.dto.AttemptState;
import vn.exampractice.examination.dto.AttemptSummary;

/**
 * Bài làm sau khi giáo viên cộng giờ hoặc khóa.
 *
 * @param deadline    hạn chót, đã gồm số phút được cộng
 * @param submittedAt rỗng khi bài còn đang làm
 */
record AttemptControlResponse(
        Long attemptId,
        Long studentId,
        AttemptState status,
        Instant deadline,
        Instant submittedAt) {

    static AttemptControlResponse from(AttemptSummary attempt) {
        return new AttemptControlResponse(attempt.attemptId(), attempt.studentId(), attempt.status(),
                attempt.deadline(), attempt.submittedAt());
    }
}
