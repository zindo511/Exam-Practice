package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.shared.error.BusinessException;

/**
 * Học sinh tham gia kỳ thi bằng mã và xem phòng chờ (ADR-0010).
 *
 * <p>Mã chỉ có hiệu lực khi kỳ thi chưa đóng, chưa hủy và còn cho tham gia. Mọi trường hợp mã không
 * dùng được đều báo chung {@code INVALID_ACCESS_CODE}, để không giúp dò mã. Học sinh đã tham gia
 * nhập lại mã vẫn vào được phòng chờ kể cả khi giáo viên đã khóa tham gia, miễn kỳ thi chưa kết thúc.
 */
@Service
class ParticipationService {

    /** Kết quả tham gia; {@code created} cho biết đây là lần tham gia đầu tiên (201) hay không (200). */
    record JoinResult(ParticipationResponse participation, boolean created) {
    }

    private final ExamSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final AttemptRepository attemptRepository;

    ParticipationService(ExamSessionRepository sessionRepository, SessionParticipantRepository participantRepository,
                         AttemptRepository attemptRepository) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.attemptRepository = attemptRepository;
    }

    /**
     * {@code noRollbackFor}: lỗi mã sai vẫn commit transaction, để lần nhập sai được ghi lại khi có giới
     * hạn nhập sai (ADR-0010). Trước khi ném lỗi, hàm này không ghi gì khác.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    JoinResult join(Long studentId, String accessCode) {
        // Giới hạn nhập sai (ADR-0010): khi có JoinAttemptLimiter, lấy now = Instant.now() một lần ở đây,
        // gọi checkNotBlocked(studentId, now) trước khi tra mã, và recordFailure(studentId, now) ngay
        // trước mỗi chỗ ném INVALID_ACCESS_CODE bên dưới.
        ExamSession session = sessionRepository.findByAccessCode(normalize(accessCode))
                .filter(ParticipationService::isActive)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.INVALID_ACCESS_CODE));

        Optional<SessionParticipant> existing =
                participantRepository.findBySessionIdAndStudentId(session.getId(), studentId);
        if (existing.isPresent()) {
            return new JoinResult(toResponse(session, existing.get()), false);
        }
        if (!session.isJoinOpen()) {
            throw new BusinessException(ExaminationErrorCode.INVALID_ACCESS_CODE);
        }
        SessionParticipant participant =
                participantRepository.save(new SessionParticipant(session.getId(), studentId, Instant.now()));
        return new JoinResult(toResponse(session, participant), true);
    }

    /** Phòng chờ; chưa tham gia kỳ thi này thì báo {@code SESSION_NOT_FOUND} như kỳ thi không tồn tại. */
    @Transactional(readOnly = true)
    ParticipationResponse get(Long studentId, Long sessionId) {
        SessionParticipant participant = participantRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.SESSION_NOT_FOUND));
        ExamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.SESSION_NOT_FOUND));
        return toResponse(session, participant);
    }

    private ParticipationResponse toResponse(ExamSession session, SessionParticipant participant) {
        Attempt attempt = attemptRepository
                .findBySessionIdAndStudentId(session.getId(), participant.getStudentId()).orElse(null);
        return ParticipationResponse.from(session, participant, attempt);
    }

    /** Kỳ thi chưa kết thúc: mã còn hiệu lực với người đã tham gia. */
    private static boolean isActive(ExamSession session) {
        return session.getStatus() == SessionStatus.SCHEDULED || session.getStatus() == SessionStatus.OPEN;
    }

    private static String normalize(String accessCode) {
        return accessCode == null ? "" : accessCode.strip().toUpperCase(Locale.ROOT);
    }
}
