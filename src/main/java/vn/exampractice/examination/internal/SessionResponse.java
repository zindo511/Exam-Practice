package vn.exampractice.examination.internal;

import java.time.Instant;

/**
 * Kỳ thi trả cho giáo viên, dùng cho cả danh sách và chi tiết.
 *
 * @param durationMinutes rỗng tới khi mở kỳ thi
 * @param examUsable chỉ có ở kỳ thi {@code SCHEDULED}: đề còn mở được không (tồn tại, chưa lưu trữ, có
 *                   câu). {@code false} nghĩa là tới giờ job sẽ không mở được, giáo viên nên hủy và tạo kỳ
 *                   thi khác (state-machines.md mục 1). Trạng thái khác thì rỗng: đề đã chụp hoặc không cần nữa.
 */
record SessionResponse(
        Long id,
        Long examId,
        String title,
        String accessCode,
        boolean joinOpen,
        SessionStatus status,
        ResultVisibility resultVisibility,
        int maxScore,
        Instant startAt,
        Instant endAt,
        Integer durationMinutes,
        Instant openedAt,
        Instant closedAt,
        Instant createdAt,
        Boolean examUsable) {

    static SessionResponse from(ExamSession session, Boolean examUsable) {
        return new SessionResponse(session.getId(), session.getExamId(), session.getTitle(),
                session.getAccessCode(), session.isJoinOpen(), session.getStatus(), session.getResultVisibility(),
                session.getMaxScore(), session.getStartAt(),
                session.getEndAt(), session.getDurationMinutes(), session.getOpenedAt(), session.getClosedAt(),
                session.getCreatedAt(), examUsable);
    }
}
