package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

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
 * <p>Mọi hàm nhận {@code teacherId} của người gọi (ADR-0015) và chỉ thao tác trên kỳ thi của người
 * đó; kỳ thi của người khác báo {@code SESSION_NOT_FOUND} (404). Luật chuyển trạng thái nằm trong
 * {@link ExamSession}.
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

    private final ExamSessionRepository repository;
    private final ExamDesignApi examDesignApi;
    private final AccessCodeGenerator codeGenerator;

    ExamSessionService(ExamSessionRepository repository, ExamDesignApi examDesignApi,
                       AccessCodeGenerator codeGenerator) {
        this.repository = repository;
        this.examDesignApi = examDesignApi;
        this.codeGenerator = codeGenerator;
    }

    /** Đề phải dùng được (của giáo viên này, chưa lưu trữ, có câu); tiêu đề kỳ thi sao chép từ đề. */
    @Transactional
    SessionResponse create(Long teacherId, SessionRequest request) {
        ExamSummary exam = examDesignApi.getExamSummary(request.examId(), teacherId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.EXAM_NOT_AVAILABLE));
        ExamSession session = ExamSession.schedule(teacherId, exam.id(), exam.title(), request.startAt(),
                request.endAt(), request.resultVisibility(), request.maxScoreOrDefault(), uniqueCode(), Instant.now());
        return SessionResponse.from(repository.save(session));
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
        return PageResponse.from(page.map(SessionResponse::from));
    }

    @Transactional(readOnly = true)
    SessionResponse get(Long teacherId, Long sessionId) {
        return SessionResponse.from(findOwned(teacherId, sessionId));
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
        return SessionResponse.from(session);
    }

    /** Kết thúc sớm kỳ thi đang mở. Chuyển các bài đang làm sang EXPIRED sẽ thêm khi có bài làm. */
    @Transactional
    SessionResponse close(Long teacherId, Long sessionId) {
        return change(teacherId, sessionId, session -> session.close(Instant.now()));
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
        return SessionResponse.from(session);
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
