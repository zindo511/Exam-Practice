package vn.exampractice.proctoring.internal;

import java.time.Instant;
import java.util.List;

import vn.exampractice.examination.dto.SessionAttempts;
import vn.exampractice.examination.dto.SessionState;

/**
 * Bảng giám sát của một kỳ thi. Không phân trang: giáo viên cần thấy cả lớp cùng lúc.
 *
 * @param totalQuestions số câu của đề; 0 khi kỳ thi chưa mở
 * @param serverTime     giờ server lúc trả response, để giao diện tính thời gian còn lại theo giờ server
 * @param students       theo thứ tự vào phòng chờ
 */
record MonitorResponse(
        Long sessionId,
        String title,
        SessionState status,
        Instant startAt,
        Instant endAt,
        int totalQuestions,
        Instant serverTime,
        List<MonitorRow> students) {

    static MonitorResponse from(SessionAttempts session, Instant serverTime, List<MonitorRow> students) {
        return new MonitorResponse(session.sessionId(), session.title(), session.status(), session.startAt(),
                session.endAt(), session.totalQuestions(), serverTime, students);
    }
}
