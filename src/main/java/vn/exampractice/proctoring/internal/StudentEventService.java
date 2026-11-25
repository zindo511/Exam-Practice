package vn.exampractice.proctoring.internal;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.examination.ExaminationApi;
import vn.exampractice.examination.dto.AttemptRef;
import vn.exampractice.examination.dto.AttemptState;
import vn.exampractice.shared.error.BusinessException;

/** Nhận sự kiện từ trình duyệt học sinh và ghi vào nhật ký giám sát (ADR-0019). */
@Service
class StudentEventService {

    private final ProctorEventRepository eventRepository;
    private final ExaminationApi examinationApi;

    StudentEventService(ProctorEventRepository eventRepository, ExaminationApi examinationApi) {
        this.eventRepository = eventRepository;
        this.examinationApi = examinationApi;
    }

    /**
     * Ghi một sự kiện. Bài làm không tồn tại hoặc của học sinh khác báo {@code ATTEMPT_NOT_FOUND}.
     *
     * <p>Bài không còn đang làm thì bỏ qua, không báo lỗi: sự kiện có thể đến ngay sau lúc nộp bài và khi đó
     * không tính là vi phạm.
     */
    @Transactional
    void report(Long studentId, Long attemptId, BrowserEventType type, Instant occurredAt) {
        AttemptRef attempt = examinationApi.findAttemptOfStudent(attemptId, studentId)
                .orElseThrow(() -> new BusinessException(ProctoringErrorCode.ATTEMPT_NOT_FOUND));
        if (attempt.status() != AttemptState.IN_PROGRESS) {
            return;
        }
        ProctorEvent event = ProctorEvent.of(attempt.attemptId(), attempt.sessionId(), attempt.studentId(),
                type.toEventType(), null, occurredAt, Instant.now());
        eventRepository.save(event);
    }
}
