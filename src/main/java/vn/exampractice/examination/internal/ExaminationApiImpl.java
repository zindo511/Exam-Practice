package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.examination.ExaminationApi;
import vn.exampractice.examination.dto.AttemptRef;
import vn.exampractice.examination.dto.AttemptState;
import vn.exampractice.examination.dto.AttemptSummary;
import vn.exampractice.examination.dto.SessionAttempts;
import vn.exampractice.examination.dto.SessionState;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.error.CommonErrorCode;

/**
 * Cài đặt {@link ExaminationApi}. Hàm đọc chạy trong transaction chỉ đọc. Hàm lệnh chạy qua
 * {@link ConflictRetry} (không gắn {@code @Transactional}): mỗi lần thử tải lại bài làm với khóa
 * {@code OPTIMISTIC_FORCE_INCREMENT}, kiểm tra lại quyền và luật, nên không bao giờ ghi đè một bài vừa
 * được nộp hay chốt (ADR-0017). Luật trạng thái nằm trong {@link Attempt}.
 */
@Service
class ExaminationApiImpl implements ExaminationApi {

    private final ExamSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final AttemptRepository attemptRepository;
    private final ConflictRetry retry;

    ExaminationApiImpl(ExamSessionRepository sessionRepository,
                       SessionParticipantRepository participantRepository,
                       AttemptRepository attemptRepository,
                       ConflictRetry retry) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.attemptRepository = attemptRepository;
        this.retry = retry;
    }

    /**
     * Ba truy vấn cố định, không phụ thuộc số học sinh: danh sách tham gia, bài làm, số câu đã trả lời
     * (đếm trong database, không tải đáp án). Ghép theo ID trong bộ nhớ (ADR-0007).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<SessionAttempts> getSessionAttempts(Long sessionId, Long teacherId) {
        return sessionRepository.findByIdAndTeacherId(sessionId, teacherId).map(session -> {
            Map<Long, Attempt> attemptByStudent = new HashMap<>();
            for (Attempt attempt : attemptRepository.findBySessionId(sessionId)) {
                attemptByStudent.put(attempt.getStudentId(), attempt);
            }
            Map<Long, Long> answeredByAttempt = new HashMap<>();
            for (AttemptRepository.AnsweredCount count : attemptRepository.countAnswersBySession(sessionId)) {
                answeredByAttempt.put(count.getAttemptId(), count.getAnswered());
            }
            List<AttemptSummary> rows = participantRepository.findBySessionIdOrderByJoinedAtAscIdAsc(sessionId)
                    .stream()
                    .map(participant -> {
                        Attempt attempt = attemptByStudent.get(participant.getStudentId());
                        if (attempt == null) {
                            return notStarted(session.getId(), participant.getStudentId());
                        }
                        return summary(attempt, answeredByAttempt.getOrDefault(attempt.getId(), 0L).intValue());
                    })
                    .toList();
            return new SessionAttempts(session.getId(), session.getTitle(), toState(session.getStatus()),
                    session.getStartAt(), session.getEndAt(), session.getQuestions().size(), rows);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttemptRef> findAttemptOfStudent(Long attemptId, Long studentId) {
        return attemptRepository.findByIdAndStudentId(attemptId, studentId)
                .map(attempt -> new AttemptRef(attempt.getId(), attempt.getSessionId(), attempt.getStudentId(),
                        toState(attempt.getStatus())));
    }

    @Override
    public AttemptSummary extendTime(Long teacherId, Long attemptId, int minutes) {
        if (minutes < 1) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED, "Số phút cộng thêm phải lớn hơn 0");
        }
        return retry.run(() -> {
            Attempt attempt = findOwnedForWrite(teacherId, attemptId).attempt();
            attempt.extendTime(minutes, Instant.now());
            return summary(attempt, attempt.answeredCount());
        });
    }

    @Override
    public AttemptSummary lock(Long teacherId, Long attemptId) {
        return retry.run(() -> {
            OwnedAttempt owned = findOwnedForWrite(teacherId, attemptId);
            owned.attempt().lock(owned.session().grading(), Instant.now());
            return summary(owned.attempt(), owned.attempt().answeredCount());
        });
    }

    private record OwnedAttempt(Attempt attempt, ExamSession session) {
    }

    /**
     * Bài làm để giáo viên ghi; bài không tồn tại, hoặc thuộc kỳ thi của giáo viên khác, đều báo
     * {@code ATTEMPT_NOT_FOUND}, không phân biệt.
     */
    private OwnedAttempt findOwnedForWrite(Long teacherId, Long attemptId) {
        Attempt attempt = attemptRepository.findForTeacherWrite(attemptId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.ATTEMPT_NOT_FOUND));
        ExamSession session = sessionRepository.findByIdAndTeacherId(attempt.getSessionId(), teacherId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.ATTEMPT_NOT_FOUND));
        return new OwnedAttempt(attempt, session);
    }

    private static AttemptSummary notStarted(Long sessionId, Long studentId) {
        return new AttemptSummary(sessionId, studentId, null, AttemptState.NOT_STARTED, 0, null, null, null);
    }

    private static AttemptSummary summary(Attempt attempt, int answeredCount) {
        return new AttemptSummary(attempt.getSessionId(), attempt.getStudentId(), attempt.getId(),
                toState(attempt.getStatus()), answeredCount, attempt.getStartedAt(), attempt.getDeadline(),
                attempt.getSubmittedAt());
    }

    private static AttemptState toState(AttemptStatus status) {
        return switch (status) {
            case IN_PROGRESS -> AttemptState.IN_PROGRESS;
            case SUBMITTED -> AttemptState.SUBMITTED;
            case EXPIRED -> AttemptState.EXPIRED;
            case LOCKED -> AttemptState.LOCKED;
        };
    }

    private static SessionState toState(SessionStatus status) {
        return switch (status) {
            case SCHEDULED -> SessionState.SCHEDULED;
            case OPEN -> SessionState.OPEN;
            case CLOSED -> SessionState.CLOSED;
            case CANCELLED -> SessionState.CANCELLED;
        };
    }
}
