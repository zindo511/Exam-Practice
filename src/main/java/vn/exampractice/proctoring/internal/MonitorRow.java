package vn.exampractice.proctoring.internal;

import java.time.Instant;

import vn.exampractice.examination.dto.AttemptState;
import vn.exampractice.examination.dto.AttemptSummary;
import vn.exampractice.identity.dto.UserSummary;

/**
 * Một dòng trên bảng giám sát: một học sinh đã tham gia kỳ thi.
 *
 * @param username       rỗng nếu không tìm thấy tài khoản
 * @param fullName       rỗng nếu không tìm thấy tài khoản
 * @param attemptId      rỗng nếu chưa bắt đầu làm bài
 * @param startedAt      rỗng nếu chưa bắt đầu làm bài
 * @param deadline       rỗng nếu chưa bắt đầu làm bài
 * @param submittedAt    rỗng khi bài chưa kết thúc
 * @param violationCount số lần rời tab và thoát toàn màn hình
 */
record MonitorRow(
        Long studentId,
        String username,
        String fullName,
        Long attemptId,
        AttemptState status,
        int answeredCount,
        Instant startedAt,
        Instant deadline,
        Instant submittedAt,
        long violationCount) {

    /** @param user {@code null} nếu không tìm thấy tài khoản */
    static MonitorRow from(AttemptSummary attempt, UserSummary user, long violationCount) {
        String username = user == null ? null : user.username();
        String fullName = user == null ? null : user.fullName();
        return new MonitorRow(attempt.studentId(), username, fullName, attempt.attemptId(), attempt.status(),
                attempt.answeredCount(), attempt.startedAt(), attempt.deadline(), attempt.submittedAt(),
                violationCount);
    }
}
