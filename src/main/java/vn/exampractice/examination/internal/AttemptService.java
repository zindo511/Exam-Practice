package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.error.CommonErrorCode;

/**
 * Bài làm: bắt đầu, lưu đáp án, nộp và chấm (state-machines.md mục 2), cùng các thao tác chốt bài
 * cho kỳ thi đóng và job hết giờ (ADR-0013).
 *
 * <p>Hàm của học sinh nhận {@code studentId} của người gọi (ADR-0015); bài của người khác báo
 * {@code ATTEMPT_NOT_FOUND}. {@code now} luôn là giờ server. Luật chuyển trạng thái nằm trong
 * {@link Attempt}; service chỉ tải dữ liệu, kiểm tra điều kiện cần dữ liệu bên ngoài bài làm (kỳ
 * thi, tham gia) và chuyển sang DTO.
 */
@Service
class AttemptService {

    private final AttemptRepository attemptRepository;
    private final ExamSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;

    AttemptService(AttemptRepository attemptRepository, ExamSessionRepository sessionRepository,
                   SessionParticipantRepository participantRepository) {
        this.attemptRepository = attemptRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
    }

    /**
     * Bắt đầu làm bài. Thứ tự kiểm tra: đã tham gia ({@code NOT_A_PARTICIPANT}, cũng là lỗi khi kỳ thi
     * không tồn tại, để không lộ ID), kỳ thi đang mở ({@code SESSION_NOT_OPEN}), chưa có bài
     * ({@code ATTEMPT_ALREADY_STARTED}).
     */
    @Transactional
    AttemptResponse start(Long studentId, Long sessionId) {
        if (!participantRepository.existsBySessionIdAndStudentId(sessionId, studentId)) {
            throw new BusinessException(ExaminationErrorCode.NOT_A_PARTICIPANT);
        }
        ExamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.NOT_A_PARTICIPANT));
        Instant now = Instant.now();
        if (session.getStatus() != SessionStatus.OPEN || !now.isBefore(session.getEndAt())) {
            throw new BusinessException(ExaminationErrorCode.SESSION_NOT_OPEN);
        }
        if (attemptRepository.existsBySessionIdAndStudentId(sessionId, studentId)) {
            throw new BusinessException(ExaminationErrorCode.ATTEMPT_ALREADY_STARTED);
        }
        Attempt attempt = Attempt.start(sessionId, studentId, session.getDurationMinutes(), session.getEndAt(), now);
        try {
            // flush ngay để hai request bắt đầu cùng lúc va vào UNIQUE (session_id, student_id) tại đây.
            attempt = attemptRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ExaminationErrorCode.ATTEMPT_ALREADY_STARTED);
        }
        return AttemptResponse.from(attempt, session, now);
    }

    /** Tải lại trang thì làm tiếp được (ADR-0012); bài đã kết thúc thì kèm điểm. */
    @Transactional(readOnly = true)
    AttemptResponse get(Long studentId, Long attemptId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .filter(a -> a.isOwnedBy(studentId))
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.ATTEMPT_NOT_FOUND));
        return AttemptResponse.from(attempt, sessionOf(attempt), Instant.now());
    }

    /** Thêm hoặc đổi đáp án của câu thứ {@code position} (từ 1). */
    @Transactional
    AnswerResponse saveAnswer(Long studentId, Long attemptId, int position, int selectedOption) {
        Attempt attempt = findOwnedForUpdate(studentId, attemptId);
        Instant now = Instant.now();
        attempt.ensureInProgress(now);
        SessionQuestion question = questionAt(sessionOf(attempt), position);
        attempt.saveAnswer(question.getId(), question.getOptions().size(), selectedOption, now);
        return new AnswerResponse(position, selectedOption, now, attempt.getDeadline());
    }

    /** Bỏ chọn câu thứ {@code position}; câu chưa trả lời thì không làm gì. */
    @Transactional
    void clearAnswer(Long studentId, Long attemptId, int position) {
        Attempt attempt = findOwnedForUpdate(studentId, attemptId);
        Instant now = Instant.now();
        attempt.ensureInProgress(now);
        attempt.clearAnswer(questionAt(sessionOf(attempt), position).getId(), now);
    }

    /** Nộp bài và chấm điểm ngay (ADR-0016). */
    @Transactional
    AttemptResponse submit(Long studentId, Long attemptId) {
        Attempt attempt = findOwnedForUpdate(studentId, attemptId);
        ExamSession session = sessionOf(attempt);
        Instant now = Instant.now();
        attempt.submit(AnswerKey.of(session.getQuestions()), now);
        return AttemptResponse.from(attempt, session, now);
    }

    /** Kết quả của mọi bài làm trong một kỳ thi của giáo viên. */
    @Transactional(readOnly = true)
    List<AttemptSummaryResponse> listForSession(Long teacherId, Long sessionId) {
        ExamSession session = sessionRepository.findByIdAndTeacherId(sessionId, teacherId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.SESSION_NOT_FOUND));
        int total = session.getQuestions().size();
        return attemptRepository.findBySessionIdOrderByStartedAtAscIdAsc(sessionId).stream()
                .map(attempt -> AttemptSummaryResponse.from(attempt, total))
                .toList();
    }

    /**
     * Kỳ thi vừa đóng: mọi bài đang làm chuyển {@code EXPIRED} và được chấm (state-machines.md mục 1).
     * Chạy trong transaction của thao tác đóng kỳ thi, để kỳ thi và bài làm đổi cùng lúc.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void expireAllInSession(ExamSession session, Instant closedAt) {
        List<Attempt> inProgress = attemptRepository.findBySessionIdAndStatus(session.getId(), AttemptStatus.IN_PROGRESS);
        if (inProgress.isEmpty()) {
            return;
        }
        AnswerKey key = AnswerKey.of(session.getQuestions());
        inProgress.forEach(attempt -> attempt.expireOnSessionClose(key, closedAt));
    }

    /**
     * Cho job chốt bài hết giờ (ADR-0013): mỗi bài một transaction riêng để một bài lỗi không làm hỏng
     * cả lượt. Bài đã kết thúc hoặc chưa quá hạn chót cộng ân hạn thì bỏ qua (học sinh vừa nộp).
     *
     * @return {@code true} nếu bài vừa được chốt
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean expireIfOverdue(Long attemptId, Instant now) {
        Attempt attempt = attemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus() != AttemptStatus.IN_PROGRESS || !attempt.isOverdue(now)) {
            return false;
        }
        attempt.expire(AnswerKey.of(sessionOf(attempt).getQuestions()), now);
        return true;
    }

    private Attempt findOwnedForUpdate(Long studentId, Long attemptId) {
        return attemptRepository.findForUpdate(attemptId)
                .filter(a -> a.isOwnedBy(studentId))
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.ATTEMPT_NOT_FOUND));
    }

    private ExamSession sessionOf(Attempt attempt) {
        return sessionRepository.findById(attempt.getSessionId())
                .orElseThrow(() -> new IllegalStateException("attempt " + attempt.getId() + " has no session"));
    }

    private static SessionQuestion questionAt(ExamSession session, int position) {
        return session.getQuestions().stream()
                .filter(q -> q.getPosition() == position)
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "Kỳ thi không có câu số " + position));
    }
}
