package vn.exampractice.examination.internal;

import java.time.Instant;

/**
 * Kỳ thi trả cho học sinh đã tham gia: đủ để hiện phòng chờ và biết khi nào kỳ thi mở. Không có mã
 * tham gia và ID đề, vì đó là thông tin của giáo viên.
 *
 * @param durationMinutes rỗng tới khi kỳ thi mở
 */
record MySessionResponse(
        Long id,
        String title,
        SessionStatus status,
        Instant startAt,
        Instant endAt,
        Integer durationMinutes) {

    static MySessionResponse from(ExamSession session) {
        return new MySessionResponse(session.getId(), session.getTitle(), session.getStatus(),
                session.getStartAt(), session.getEndAt(), session.getDurationMinutes());
    }
}
