package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.examdesign.ExamDesignApi;
import vn.exampractice.examdesign.dto.ExamSnapshot;
import vn.exampractice.examdesign.dto.ExamSummary;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.web.PageResponse;

/**
 * Kỳ thi phía giáo viên: tạo, danh sách, xem, mở, kết thúc, hủy, khóa tham gia.
 *
 * <p>Hàm cho giáo viên nhận {@code teacherId} của người gọi (ADR-0015) và chỉ thao tác trên kỳ thi
 * của người đó; kỳ thi của người khác báo {@code SESSION_NOT_FOUND} (404). Hai hàm {@code ...OnSchedule}
 * dành cho {@link ExaminationJob}, mỗi lần gọi là một transaction cho một kỳ thi. Luật chuyển trạng
 * thái nằm trong {@link ExamSession}.
 *
 * <p>Đề lấy qua {@link ExamDesignApi} (ADR-0004); DTO của examdesign được chuyển sang kiểu của
 * examination ngay tại đây.
 */
@Service
class ExamSessionService {

    /** Thứ tự mặc định khi client không gửi {@code sort}: giờ bắt đầu muộn nhất trước. */
    private static final Sort LATEST_START_FIRST = Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id"));

    /** Số lần sinh lại khi mã mới trùng mã đã có; với 2,2 tỷ tổ hợp, trùng 5 lần liên tiếp gần như không xảy ra. */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(ExamSessionService.class);

    private final ExamSessionRepository repository;
    private final AttemptRepository attemptRepository;
    private final ExamDesignApi examDesignApi;
    private final AccessCodeGenerator codeGenerator;
    private final ConflictRetry retry;

    ExamSessionService(ExamSessionRepository repository, AttemptRepository attemptRepository,
                       ExamDesignApi examDesignApi, AccessCodeGenerator codeGenerator, ConflictRetry retry) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
        this.examDesignApi = examDesignApi;
        this.codeGenerator = codeGenerator;
        this.retry = retry;
    }

    /** Đề phải dùng được (của giáo viên này, chưa lưu trữ, có câu); tiêu đề kỳ thi sao chép từ đề. */
    @Transactional
    SessionResponse create(Long teacherId, SessionRequest request) {
        ExamSummary exam = examDesignApi.getExamSummary(request.examId(), teacherId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.EXAM_NOT_AVAILABLE));
        ExamSession session = ExamSession.schedule(teacherId, exam.id(), exam.title(), request.startAt(),
                request.endAt(), request.resultVisibility(), request.maxScoreOrDefault(), uniqueCode(), Instant.now());
        return SessionResponse.from(repository.save(session), true);
    }

    /** @param status lọc theo trạng thái; rỗng thì lấy tất cả */
    @Transactional(readOnly = true)
    PageResponse<SessionResponse> list(Long teacherId, SessionStatus status, Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_START_FIRST);
        Page<ExamSession> page = status == null
                ? repository.findByTeacherId(teacherId, sorted)
                : repository.findByTeacherIdAndStatus(teacherId, status, sorted);
        return PageResponse.from(page.map(responder(teacherId, page.getContent())));
    }

    @Transactional(readOnly = true)
    SessionResponse get(Long teacherId, Long sessionId) {
        return respond(teacherId, findOwned(teacherId, sessionId));
    }

    /**
     * Mở kỳ thi và chụp snapshot đề. Kiểm tra trạng thái trước rồi mới hỏi examdesign, để kỳ thi sai
     * trạng thái báo {@code INVALID_SESSION_STATE} chứ không báo lỗi của đề. Đề đã lưu trữ sau khi
     * lên lịch thì không mở được (state-machines.md mục 3).
     */
    @Transactional
    SessionResponse open(Long teacherId, Long sessionId) {
        ExamSession session = findOwned(teacherId, sessionId);
        Instant now = Instant.now();
        session.ensureCanOpen(now);
        ExamSnapshot snapshot = examDesignApi.getExamSnapshot(session.getExamId(), teacherId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.EXAM_NOT_AVAILABLE));
        session.open(toSnapshotQuestions(snapshot), snapshot.durationMinutes(), now);
        return respond(teacherId, session);
    }

    /**
     * Kết thúc sớm kỳ thi đang mở. Mọi bài còn đang làm chuyển sang {@code EXPIRED} và được chấm từ đáp
     * án đã lưu, ghi nhận kết thúc lúc đóng (hoặc hạn chót nếu sớm hơn), state-machines.md mục 1.
     *
     * <p>Chạy qua {@link ConflictRetry} vì có thể đụng học sinh đang lưu đáp án hay nộp bài (ADR-0017).
     */
    SessionResponse close(Long teacherId, Long sessionId) {
        return retry.run(() -> {
            ExamSession session = findOwned(teacherId, sessionId);
            closeAndExpireAttempts(session, Instant.now());
            return respond(teacherId, session);
        });
    }

    /**
     * Job mở một kỳ thi đã tới giờ mở (state-machines.md mục 1). Kiểm tra lại trên dữ liệu vừa tải, vì
     * giáo viên có thể đã mở hoặc hủy sau khi job lấy danh sách.
     *
     * <p>Đề không còn dùng được (bị lưu trữ hoặc bỏ hết câu sau khi lên lịch) thì <b>hủy kỳ thi</b>: không
     * có câu hỏi để chụp, kỳ thi không thể diễn ra. Giáo viên đã thấy cảnh báo {@code examUsable = false}
     * trước giờ mở để kịp sửa đề.
     *
     * @return {@code true} nếu kỳ thi được mở hoặc bị hủy
     */
    @Transactional
    boolean openOnSchedule(Long sessionId, Instant now) {
        ExamSession session = repository.findById(sessionId).orElse(null);
        if (session == null || !session.isDueToOpen(now)) {
            return false;
        }
        Optional<ExamSnapshot> snapshot = examDesignApi.getExamSnapshot(session.getExamId(), session.getTeacherId());
        if (snapshot.isEmpty()) {
            session.cancel();
            log.info("Hủy kỳ thi {} vì tới giờ mở mà đề {} không còn dùng được", sessionId, session.getExamId());
            return true;
        }
        session.open(toSnapshotQuestions(snapshot.get()), snapshot.get().durationMinutes(), now);
        return true;
    }

    /**
     * Job đóng một kỳ thi đã quá giờ đóng cộng ân hạn. Kỳ thi đang mở thì chốt các bài còn dở như khi
     * giáo viên kết thúc, ghi nhận đóng lúc {@code endAt}.
     *
     * <p>Kỳ thi vẫn {@code SCHEDULED} lúc này nghĩa là job chưa từng tới lượt mở nó trong khoảng giờ thi
     * (ví dụ ứng dụng tắt suốt khoảng đó): kỳ thi không diễn ra nên bị hủy, không chuyển {@code CLOSED}.
     *
     * @return {@code true} nếu kỳ thi được đóng hoặc bị hủy
     */
    @Transactional
    boolean closeOnSchedule(Long sessionId, Instant now) {
        ExamSession session = repository.findById(sessionId).orElse(null);
        if (session == null || !session.isDueToClose(now)) {
            return false;
        }
        if (session.getStatus() == SessionStatus.SCHEDULED) {
            session.cancel();
            log.info("Hủy kỳ thi {} vì đã quá giờ đóng mà chưa từng được mở", sessionId);
        } else {
            closeAndExpireAttempts(session, session.getEndAt());
        }
        return true;
    }

    /**
     * Đóng kỳ thi đang mở; mọi bài còn đang làm chuyển {@code EXPIRED}, ghi nhận kết thúc lúc đóng hoặc
     * lúc hạn chót nếu sớm hơn.
     */
    private void closeAndExpireAttempts(ExamSession session, Instant closedAt) {
        session.close(closedAt);
        Grading grading = session.grading();
        for (Attempt attempt : attemptRepository.findBySessionIdAndStatus(session.getId(), AttemptStatus.IN_PROGRESS)) {
            attempt.expire(grading, attempt.getDeadline().isBefore(closedAt) ? attempt.getDeadline() : closedAt);
        }
    }

    @Transactional
    SessionResponse cancel(Long teacherId, Long sessionId) {
        return change(teacherId, sessionId, ExamSession::cancel);
    }

    /** Đổi những gì học sinh được xem sau khi bài kết thúc; được đổi ở mọi trạng thái. */
    @Transactional
    SessionResponse changeResultVisibility(Long teacherId, Long sessionId, ResultVisibility visibility) {
        return change(teacherId, sessionId, session -> session.changeResultVisibility(visibility));
    }

    @Transactional
    SessionResponse lockJoin(Long teacherId, Long sessionId) {
        return change(teacherId, sessionId, ExamSession::lockJoin);
    }

    @Transactional
    SessionResponse unlockJoin(Long teacherId, Long sessionId) {
        return change(teacherId, sessionId, ExamSession::unlockJoin);
    }

    /** Lấy kỳ thi của giáo viên, áp một thao tác; Hibernate tự ghi thay đổi khi commit. */
    private SessionResponse change(Long teacherId, Long sessionId, Consumer<ExamSession> action) {
        ExamSession session = findOwned(teacherId, sessionId);
        action.accept(session);
        return respond(teacherId, session);
    }

    private SessionResponse respond(Long teacherId, ExamSession session) {
        return responder(teacherId, List.of(session)).apply(session);
    }

    /**
     * Cách chuyển kỳ thi của một giáo viên sang response. Hỏi examdesign một lần cho mọi kỳ thi
     * {@code SCHEDULED} trong danh sách xem đề còn dùng được không (README 6.8: gom ID, không N+1); các
     * trạng thái khác không cần hỏi.
     */
    private Function<ExamSession, SessionResponse> responder(Long teacherId, Collection<ExamSession> sessions) {
        Set<Long> scheduledExamIds = sessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.SCHEDULED)
                .map(ExamSession::getExamId)
                .collect(Collectors.toSet());
        Set<Long> usable = scheduledExamIds.isEmpty()
                ? Set.of()
                : examDesignApi.findUsableExamIds(scheduledExamIds, teacherId);
        return s -> SessionResponse.from(s,
                s.getStatus() == SessionStatus.SCHEDULED ? usable.contains(s.getExamId()) : null);
    }

    private ExamSession findOwned(Long teacherId, Long sessionId) {
        return repository.findByIdAndTeacherId(sessionId, teacherId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.SESSION_NOT_FOUND));
    }

    /**
     * Mã chưa kỳ thi nào dùng. Hai request cùng lúc vẫn có thể sinh trùng giữa lúc kiểm tra và lúc
     * ghi; khi đó ràng buộc UNIQUE trong database chặn lại.
     */
    private String uniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.next();
            if (!repository.existsByAccessCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique access code");
    }

    /** Chuyển DTO công khai của examdesign sang kiểu của examination (README 4.3). */
    private static List<ExamSession.SnapshotQuestion> toSnapshotQuestions(ExamSnapshot snapshot) {
        return snapshot.questions().stream()
                .map(q -> new ExamSession.SnapshotQuestion(q.position(), q.sourceQuestionId(), q.content(),
                        q.options(), q.correctOption()))
                .toList();
    }
}
