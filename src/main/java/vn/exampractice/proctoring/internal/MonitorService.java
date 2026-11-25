package vn.exampractice.proctoring.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.examination.ExaminationApi;
import vn.exampractice.examination.dto.AttemptSummary;
import vn.exampractice.examination.dto.SessionAttempts;
import vn.exampractice.identity.IdentityApi;
import vn.exampractice.identity.dto.UserSummary;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.web.PageResponse;

/** Màn hình giám sát của giáo viên (ADR-0011). Chỉ đọc. */
@Service
class MonitorService {

    private static final List<ProctorEventType> VIOLATION_TYPES =
            Arrays.stream(ProctorEventType.values()).filter(ProctorEventType::isViolation).toList();

    private final ExaminationApi examinationApi;
    private final IdentityApi identityApi;
    private final ProctorEventRepository eventRepository;

    MonitorService(ExaminationApi examinationApi, IdentityApi identityApi, ProctorEventRepository eventRepository) {
        this.examinationApi = examinationApi;
        this.identityApi = identityApi;
        this.eventRepository = eventRepository;
    }

    /**
     * Bảng giám sát cả lớp. Kỳ thi không tồn tại hoặc của giáo viên khác báo {@code SESSION_NOT_FOUND}.
     *
     * <ol>
     *   <li>Hỏi examination: học sinh nào đã tham gia, trạng thái và tiến độ từng bài.</li>
     *   <li>Hỏi identity: tên của các học sinh, một lần gọi.</li>
     *   <li>Đếm vi phạm của từng bài từ nhật ký, một truy vấn.</li>
     *   <li>Ghép lại thành từng dòng.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    MonitorResponse monitor(Long teacherId, Long sessionId) {
        SessionAttempts session = findOwned(teacherId, sessionId);

        List<Long> studentIds = session.attempts().stream().map(AttemptSummary::studentId).toList();
        Map<Long, UserSummary> users = identityApi.findUsers(studentIds);
        Map<Long, Long> violationsByAttempt = countViolations(sessionId);

        List<MonitorRow> rows = new ArrayList<>();
        for (AttemptSummary attempt : session.attempts()) {
            UserSummary user = users.get(attempt.studentId());
            long violations = violationsByAttempt.getOrDefault(attempt.attemptId(), 0L);
            rows.add(MonitorRow.from(attempt, user, violations));
        }
        return MonitorResponse.from(session, Instant.now(), rows);
    }

    /**
     * Nhật ký của một bài làm, luôn xếp mới nhất trước. Kỳ thi của giáo viên khác báo
     * {@code SESSION_NOT_FOUND}; bài làm không thuộc kỳ thi này báo {@code ATTEMPT_NOT_FOUND}.
     */
    @Transactional(readOnly = true)
    PageResponse<AttemptEventResponse> events(Long teacherId, Long sessionId, Long attemptId, Pageable pageable) {
        SessionAttempts session = findOwned(teacherId, sessionId);
        boolean inSession = session.attempts().stream().anyMatch(a -> attemptId.equals(a.attemptId()));
        if (!inSession) {
            throw new BusinessException(ProctoringErrorCode.ATTEMPT_NOT_FOUND);
        }

        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<ProctorEvent> events =
                eventRepository.findByAttemptIdOrderByReceivedAtDescIdDesc(attemptId, unsorted);
        return PageResponse.from(events.map(AttemptEventResponse::from));
    }

    private SessionAttempts findOwned(Long teacherId, Long sessionId) {
        return examinationApi.getSessionAttempts(sessionId, teacherId)
                .orElseThrow(() -> new BusinessException(ProctoringErrorCode.SESSION_NOT_FOUND));
    }

    /** Số vi phạm theo ID bài làm; bài không vi phạm thì không có trong map. */
    private Map<Long, Long> countViolations(Long sessionId) {
        Map<Long, Long> violationsByAttempt = new HashMap<>();
        for (ProctorEventRepository.EventCount count : eventRepository.countBySession(sessionId, VIOLATION_TYPES)) {
            violationsByAttempt.put(count.getAttemptId(), count.getTotal());
        }
        return violationsByAttempt;
    }
}
