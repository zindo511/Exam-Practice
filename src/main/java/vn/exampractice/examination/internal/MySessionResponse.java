package vn.exampractice.examination.internal;

import java.time.Instant;

/**
 * Kỳ thi trả cho học sinh đã tham gia: đủ để hiện phòng chờ và biết khi nào kỳ thi mở. Không có mã
 * tham gia và ID đề, vì đó là thông tin của giáo viên.
 *
 * @param durationMinutes  rỗng tới khi kỳ thi mở
 * @param resultVisibility để giao diện biết có hiện điểm, đáp án hay không
 * @param maxScore         thang điểm, để hiện điểm dạng "8.50 / 10"
 */
record MySessionResponse(
        Long id,
        String title,
        SessionStatus status,
        Instant startAt,
        Instant endAt,
        Integer durationMinutes,
        ResultVisibility resultVisibility,
        int maxScore) {

    static MySessionResponse from(ExamSession session) {
        return new MySessionResponse(session.getId(), session.getTitle(), session.getStatus(),
                session.getStartAt(), session.getEndAt(), session.getDurationMinutes(),
                session.getResultVisibility(), session.getMaxScore());
    }
}
