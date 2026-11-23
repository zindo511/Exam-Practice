package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kỳ thi trả cho học sinh đã tham gia: đủ để hiện phòng chờ và biết khi nào kỳ thi mở. Không có mã
 * tham gia và ID đề, vì đó là thông tin của giáo viên.
 *
 * @param durationMinutes  rỗng tới khi kỳ thi mở
 * @param resultVisibility để giao diện biết có hiện điểm, đáp án hay không
 * @param maxScore         thang điểm, để hiện điểm dạng "8.50 / 10"
 * @param attemptId        bài làm của học sinh trong kỳ thi; rỗng nếu chưa bắt đầu
 * @param attemptStatus    để giao diện hiện nút "Bắt đầu", "Tiếp tục" hay "Xem điểm"
 * @param correctCount     chỉ có khi bài đã kết thúc và kỳ thi không ẩn điểm
 * @param score            chỉ có khi bài đã kết thúc và kỳ thi không ẩn điểm
 */
record MySessionResponse(
        Long id,
        String title,
        SessionStatus status,
        Instant startAt,
        Instant endAt,
        Integer durationMinutes,
        ResultVisibility resultVisibility,
        int maxScore,
        Long attemptId,
        AttemptStatus attemptStatus,
        Integer correctCount,
        BigDecimal score) {

    /** @param attempt bài làm của học sinh trong kỳ thi, hoặc {@code null} nếu chưa bắt đầu */
    static MySessionResponse from(ExamSession session, Attempt attempt) {
        boolean showScore = attempt != null && attempt.getStatus().isFinished()
                && session.getResultVisibility() != ResultVisibility.HIDDEN;
        return new MySessionResponse(session.getId(), session.getTitle(), session.getStatus(),
                session.getStartAt(), session.getEndAt(), session.getDurationMinutes(),
                session.getResultVisibility(), session.getMaxScore(),
                attempt == null ? null : attempt.getId(),
                attempt == null ? null : attempt.getStatus(),
                showScore ? attempt.getCorrectCount() : null,
                showScore ? attempt.getScore() : null);
    }
}
