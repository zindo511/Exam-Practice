package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Instant;

import vn.exampractice.identity.dto.UserSummary;

/**
 * Một dòng trên bảng điểm của giáo viên: một học sinh đã tham gia kỳ thi và bài làm của học sinh đó.
 * Giáo viên luôn thấy điểm, không phụ thuộc {@link ResultVisibility}.
 *
 * @param username     rỗng nếu không tìm thấy tài khoản
 * @param fullName     rỗng nếu không tìm thấy tài khoản
 * @param attemptId    rỗng nếu học sinh chưa bắt đầu làm bài; có thì bấm vào để xem chi tiết
 * @param correctCount rỗng khi chưa làm hoặc đang làm
 * @param score        rỗng khi chưa làm hoặc đang làm
 */
record StudentResultRow(
        Long studentId,
        String username,
        String fullName,
        Instant joinedAt,
        Long attemptId,
        AttemptStatus attemptStatus,
        Instant startedAt,
        Instant submittedAt,
        Integer correctCount,
        BigDecimal score) {

    /**
     * @param user    {@code null} nếu không tìm thấy tài khoản
     * @param attempt {@code null} nếu học sinh chưa bắt đầu làm bài
     */
    static StudentResultRow from(SessionParticipant participant, UserSummary user, Attempt attempt) {
        String username = user == null ? null : user.username();
        String fullName = user == null ? null : user.fullName();
        if (attempt == null) {
            return new StudentResultRow(participant.getStudentId(), username, fullName, participant.getJoinedAt(),
                    null, null, null, null, null, null);
        }
        return new StudentResultRow(participant.getStudentId(), username, fullName, participant.getJoinedAt(),
                attempt.getId(), attempt.getStatus(), attempt.getStartedAt(), attempt.getSubmittedAt(),
                attempt.getCorrectCount(), attempt.getScore());
    }
}
