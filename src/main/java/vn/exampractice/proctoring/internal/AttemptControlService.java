package vn.exampractice.proctoring.internal;

import java.time.Instant;

import org.springframework.stereotype.Service;
import vn.exampractice.examination.ExaminationApi;
import vn.exampractice.examination.dto.AttemptSummary;

/**
 * Giáo viên can thiệp vào một bài làm: cộng giờ, khóa bài (ADR-0012). Examination kiểm tra quyền và luật,
 * rồi đổi bài làm; ở đây chỉ ghi lại thao tác vào nhật ký giám sát.
 *
 * <p>Không có {@code @Transactional}: lệnh của {@link ExaminationApi} tự thử lại khi xung đột, mỗi lần một
 * transaction mới, nên phải gọi ngoài transaction (ADR-0017). Lệnh lỗi thì không ghi nhật ký.
 */
@Service
class AttemptControlService {

    private final ExaminationApi examinationApi;
    private final ProctorEventRepository eventRepository;

    AttemptControlService(ExaminationApi examinationApi, ProctorEventRepository eventRepository) {
        this.examinationApi = examinationApi;
        this.eventRepository = eventRepository;
    }

    AttemptControlResponse extendTime(Long teacherId, Long attemptId, int minutes) {
        AttemptSummary attempt = examinationApi.extendTime(teacherId, attemptId, minutes);
        saveEvent(attempt, ProctorEventType.TEACHER_EXTENDED_TIME, "Cộng " + minutes + " phút");
        return AttemptControlResponse.from(attempt);
    }

    AttemptControlResponse lock(Long teacherId, Long attemptId) {
        AttemptSummary attempt = examinationApi.lock(teacherId, attemptId);
        saveEvent(attempt, ProctorEventType.TEACHER_LOCKED, null);
        return AttemptControlResponse.from(attempt);
    }

    private void saveEvent(AttemptSummary attempt, ProctorEventType type, String detail) {
        Instant now = Instant.now();
        ProctorEvent event = ProctorEvent.of(attempt.attemptId(), attempt.sessionId(), attempt.studentId(), type,
                detail, now, now);
        eventRepository.save(event);
    }
}
