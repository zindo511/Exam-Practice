package vn.exampractice.examination.internal;

import java.time.Instant;

/**
 * Phòng chờ của học sinh trong một kỳ thi: kỳ thi đã mở chưa, và bài làm nếu đã bắt đầu. Không có
 * mã tham gia hay nội dung đề.
 *
 * @param durationMinutes rỗng tới khi kỳ thi mở
 * @param attemptId       rỗng khi chưa bắt đầu làm bài
 * @param attemptStatus   rỗng khi chưa bắt đầu làm bài
 */
record ParticipationResponse(
        Long sessionId,
        String title,
        SessionStatus sessionStatus,
        Instant startAt,
        Instant endAt,
        Integer durationMinutes,
        Instant joinedAt,
        Long attemptId,
        AttemptStatus attemptStatus) {

    static ParticipationResponse from(ExamSession session, SessionParticipant participant, Attempt attempt) {
        return new ParticipationResponse(session.getId(), session.getTitle(), session.getStatus(),
                session.getStartAt(), session.getEndAt(), session.getDurationMinutes(), participant.getJoinedAt(),
                attempt == null ? null : attempt.getId(), attempt == null ? null : attempt.getStatus());
    }
}
