package vn.exampractice.examination.internal;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.error.CommonErrorCode;

/**
 * Học sinh làm bài: bắt đầu, xem lại, lưu đáp án, nộp (state-machines.md mục 2).
 *
 * <p>Mọi hàm nhận {@code studentId} của người gọi (ADR-0015); bài làm của người khác báo
 * {@code ATTEMPT_NOT_FOUND}. Luật trạng thái và hạn chót nằm trong {@link Attempt}.
 *
 * <p>Các thao tác ghi chạy qua {@link ConflictRetry} thay vì {@code @Transactional}, để tự thử lại khi
 * đụng thao tác ghi song song (ADR-0017). Mỗi lần thử tải lại dữ liệu và kiểm tra lại luật.
 */
@Service
class AttemptService {

    private final AttemptRepository attemptRepository;
    private final ExamSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final ConflictRetry retry;

    AttemptService(AttemptRepository attemptRepository, ExamSessionRepository sessionRepository,
                   SessionParticipantRepository participantRepository, ConflictRetry retry) {
        this.attemptRepository = attemptRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.retry = retry;
    }

    /**
     * Bắt đầu làm bài: phải đã tham gia ({@code NOT_A_PARTICIPANT}), chưa có bài làm
     * ({@code ATTEMPT_ALREADY_STARTED}), kỳ thi đang mở và chưa tới giờ đóng ({@code SESSION_NOT_OPEN}).
     * Bấm hai lần cùng lúc: lần thứ hai đụng UNIQUE, thử lại và nhận {@code ATTEMPT_ALREADY_STARTED}.
     */
    AttemptResponse start(Long studentId, Long sessionId) {
        return retry.run(() -> {
            Instant now = Instant.now();
            if (!participantRepository.existsBySessionIdAndStudentId(sessionId, studentId)) {
                throw new BusinessException(ExaminationErrorCode.NOT_A_PARTICIPANT);
            }
            if (attemptRepository.existsBySessionIdAndStudentId(sessionId, studentId)) {
                throw new BusinessException(ExaminationErrorCode.ATTEMPT_ALREADY_STARTED);
            }
            ExamSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new BusinessException(ExaminationErrorCode.NOT_A_PARTICIPANT));
            session.ensureAcceptsAttempts(now);
            Attempt attempt = attemptRepository.saveAndFlush(Attempt.start(sessionId, studentId,
                    session.getDurationMinutes(), session.getEndAt(), now));
            return AttemptResponse.of(attempt, session, now);
        });
    }

    /** Tải lại bài làm, ví dụ sau khi thoát ra rồi vào lại; đáp án đúng chỉ hiện theo cài đặt của kỳ thi. */
    @Transactional(readOnly = true)
    AttemptResponse get(Long studentId, Long attemptId) {
        Attempt attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.ATTEMPT_NOT_FOUND));
        return AttemptResponse.of(attempt, sessionOf(attempt), Instant.now());
    }

    /**
     * Lưu đáp án của một câu. Kiểm tra bài còn nhận ghi trước, để bài đã hết giờ báo hết giờ chứ không
     * báo lỗi của câu hỏi.
     */
    void saveAnswer(Long studentId, Long attemptId, Long questionId, int selectedOption) {
        retry.run(() -> {
            Instant now = Instant.now();
            Attempt attempt = findForWrite(studentId, attemptId);
            attempt.ensureAcceptingWrites(now);
            SessionQuestion question = sessionOf(attempt).question(questionId)
                    .orElseThrow(() -> new BusinessException(ExaminationErrorCode.SESSION_QUESTION_NOT_FOUND));
            if (selectedOption >= question.getOptions().size()) {
                throw new BusinessException(CommonErrorCode.MALFORMED_REQUEST,
                        "Câu hỏi chỉ có " + question.getOptions().size() + " lựa chọn");
            }
            attempt.saveAnswer(questionId, selectedOption, now);
        });
    }

    /** Nộp bài và chấm ngay theo thang điểm của kỳ thi; kết quả trả về tùy cài đặt xem kết quả. */
    AttemptResponse submit(Long studentId, Long attemptId) {
        return retry.run(() -> {
            Instant now = Instant.now();
            Attempt attempt = findForWrite(studentId, attemptId);
            ExamSession session = sessionOf(attempt);
            attempt.submit(session.grading(), now);
            return AttemptResponse.of(attempt, session, now);
        });
    }

    private Attempt findForWrite(Long studentId, Long attemptId) {
        return attemptRepository.findForWrite(attemptId, studentId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.ATTEMPT_NOT_FOUND));
    }

    private ExamSession sessionOf(Attempt attempt) {
        return sessionRepository.findById(attempt.getSessionId())
                .orElseThrow(() -> new IllegalStateException("Session of attempt " + attempt.getId() + " is missing"));
    }
}
