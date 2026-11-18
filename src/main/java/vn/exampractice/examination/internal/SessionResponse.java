package vn.exampractice.examination.internal;

import java.time.Instant;

/**
 * Kỳ thi trả cho giáo viên, dùng cho cả danh sách và chi tiết.
 *
 * @param durationMinutes rỗng tới khi mở kỳ thi
 */
record SessionResponse(
        Long id,
        Long examId,
        String title,
        String accessCode,
        boolean joinOpen,
        SessionStatus status,
        Instant startAt,
        Instant endAt,
        Integer durationMinutes,
        Instant openedAt,
        Instant closedAt,
        Instant createdAt) {

    static SessionResponse from(ExamSession session) {
        return new SessionResponse(session.getId(), session.getExamId(), session.getTitle(),
                session.getAccessCode(), session.isJoinOpen(), session.getStatus(), session.getStartAt(),
                session.getEndAt(), session.getDurationMinutes(), session.getOpenedAt(), session.getClosedAt(),
                session.getCreatedAt());
    }
}
