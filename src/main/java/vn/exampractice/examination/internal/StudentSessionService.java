package vn.exampractice.examination.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.web.PageResponse;

/**
 * Phía học sinh của kỳ thi: nhập mã để tham gia, và quay lại phòng chờ qua danh sách đã tham gia
 * (ADR-0010).
 *
 * <p>Mọi hàm nhận {@code studentId} của người gọi (ADR-0015). Học sinh chỉ thấy kỳ thi mình đã có tên
 * trong {@link SessionParticipant}; kỳ thi khác báo {@code SESSION_NOT_FOUND}.
 */
@Service
class StudentSessionService {

    /** Thứ tự mặc định của "Kỳ thi của tôi": giờ bắt đầu muộn nhất trước. */
    private static final Sort LATEST_START_FIRST = Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id"));

    private final ExamSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final JoinFailureRepository failureRepository;
    private final JoinFailureRecorder failureRecorder;

    StudentSessionService(ExamSessionRepository sessionRepository,
                          SessionParticipantRepository participantRepository,
                          JoinFailureRepository failureRepository,
                          JoinFailureRecorder failureRecorder) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.failureRepository = failureRepository;
        this.failureRecorder = failureRecorder;
    }

    /**
     * Nhập mã để vào phòng chờ.
     *
     * <ol>
     *   <li>Đang bị tạm chặn vì sai quá nhiều thì báo {@code ACCESS_CODE_ATTEMPTS_EXCEEDED} (429), không
     *       kiểm tra mã.</li>
     *   <li>Đã có tên trong kỳ thi của mã này thì trả lại kỳ thi, kể cả khi giáo viên đã khóa tham
     *       gia: nhập lại mã không bao giờ làm mất chỗ.</li>
     *   <li>Kỳ thi còn nhận tham gia thì thêm tên vào danh sách.</li>
     *   <li>Kỳ thi đã kết thúc hoặc đã khóa tham gia: báo {@code INVALID_ACCESS_CODE}, <b>không</b>
     *       tính là một lần sai, vì mã đúng; học sinh đến muộn bấm thử nhiều lần không bị chặn.</li>
     *   <li>Mã không khớp kỳ thi nào: ghi một lần sai rồi báo {@code INVALID_ACCESS_CODE}. Thông báo
     *       giống hệt trường hợp trên.</li>
     * </ol>
     */
    @Transactional
    MySessionResponse join(Long studentId, String rawCode) {
        Instant now = Instant.now();
        requireNotBlocked(studentId, now);

        Optional<ExamSession> found = sessionRepository.findByAccessCode(normalize(rawCode));
        if (found.isEmpty()) {
            failureRecorder.record(studentId, now);
            throw new BusinessException(ExaminationErrorCode.INVALID_ACCESS_CODE);
        }
        ExamSession session = found.get();
        if (participantRepository.existsBySessionIdAndStudentId(session.getId(), studentId)) {
            return MySessionResponse.from(session);
        }
        if (!session.acceptsJoin()) {
            throw new BusinessException(ExaminationErrorCode.INVALID_ACCESS_CODE);
        }
        participantRepository.save(SessionParticipant.join(session.getId(), studentId, now));
        return MySessionResponse.from(session);
    }

    /** Các kỳ thi đã tham gia, để quay lại phòng chờ mà không nhập lại mã. */
    @Transactional(readOnly = true)
    PageResponse<MySessionResponse> mySessions(Long studentId, Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_START_FIRST);
        return PageResponse.from(sessionRepository.findJoinedBy(studentId, sorted).map(MySessionResponse::from));
    }

    /** Phòng chờ của một kỳ thi; chưa tham gia thì coi như không có. */
    @Transactional(readOnly = true)
    MySessionResponse mySession(Long studentId, Long sessionId) {
        if (!participantRepository.existsBySessionIdAndStudentId(sessionId, studentId)) {
            throw new BusinessException(ExaminationErrorCode.SESSION_NOT_FOUND);
        }
        return sessionRepository.findById(sessionId)
                .map(MySessionResponse::from)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.SESSION_NOT_FOUND));
    }

    private void requireNotBlocked(Long studentId, Instant now) {
        List<Instant> recent = failureRepository
                .findTop5ByStudentIdAndFailedAtAfterOrderByFailedAtDesc(studentId, now.minus(JoinAttemptLimiter.LOOKBACK))
                .stream().map(JoinFailure::getFailedAt).toList();
        JoinAttemptLimiter.blockedUntil(recent, now).ifPresent(until -> {
            long minutes = Math.max(1, (Duration.between(now, until).toSeconds() + 59) / 60);
            throw new BusinessException(ExaminationErrorCode.ACCESS_CODE_ATTEMPTS_EXCEEDED,
                    "Nhập sai mã quá nhiều lần, thử lại sau khoảng " + minutes + " phút");
        });
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
