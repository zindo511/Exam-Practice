package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.exampractice.shared.error.BusinessException;

/**
 * Kỳ thi trong bảng {@code exam_session} (database.md mục 5), vòng đời theo state-machines.md mục 1:
 * {@code SCHEDULED → OPEN → CLOSED}, hoặc {@code SCHEDULED → CANCELLED}.
 *
 * <p>Mỗi thao tác là một hàm hành vi kiểm tra trạng thái trước khi đổi; sai trạng thái báo
 * {@code INVALID_SESSION_STATE} (409).
 *
 * <p>{@code examId} và {@code teacherId} chỉ lưu số, không có foreign key xuyên module (ADR-0007).
 * Khi mở, kỳ thi chụp bản sao các câu hỏi ({@link SessionQuestion}, {@code @OneToMany} theo README
 * 6.8) và thời lượng (ADR-0003); từ đó không cần {@code examId} nữa.
 *
 * <p>{@code @Version} phát hiện giáo viên và job cùng mở hoặc đóng một kỳ thi.
 */
@Entity
@Table(name = "exam_session")
class ExamSession {

    static final int ACCESS_CODE_LENGTH = 6;
    static final int MAX_TITLE_LENGTH = 200;

    /** Một câu trong snapshot đề, đã chuyển từ DTO của examdesign sang kiểu của examination. */
    record SnapshotQuestion(int position, Long sourceQuestionId, String content, List<String> options,
                            int correctOption) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_id", nullable = false, updatable = false)
    private Long examId;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private Long teacherId;

    /** Sao chép từ tiêu đề đề lúc tạo kỳ thi. */
    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    /** Cột {@code CHAR(6)}; {@code @JdbcTypeCode} để Hibernate không coi là VARCHAR khi kiểm tra schema. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "access_code", nullable = false, unique = true, length = ACCESS_CODE_LENGTH)
    private String accessCode;

    @Column(name = "join_open", nullable = false)
    private boolean joinOpen;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    /** Chụp từ đề lúc mở; rỗng trước đó. */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "session_id", nullable = false)
    @OrderBy("position")
    private List<SessionQuestion> questions = new ArrayList<>();

    /** Chỉ dành cho JPA. */
    protected ExamSession() {
    }

    private ExamSession(Long teacherId, Long examId, Instant now) {
        this.teacherId = teacherId;
        this.examId = examId;
        this.createdAt = now;
    }

    /**
     * Lên lịch kỳ thi mới ở trạng thái {@code SCHEDULED}, cho phép tham gia ngay. Việc đề có dùng
     * được không do service hỏi {@code ExamDesignApi} trước khi gọi hàm này.
     *
     * @param title tiêu đề đề tại thời điểm tạo
     */
    static ExamSession schedule(Long teacherId, Long examId, String title, Instant startAt, Instant endAt,
                                String accessCode, Instant now) {
        Objects.requireNonNull(teacherId, "teacherId");
        Objects.requireNonNull(examId, "examId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(startAt, "startAt");
        Objects.requireNonNull(endAt, "endAt");
        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("startAt must be before endAt");
        }
        if (!now.isBefore(endAt)) {
            throw new IllegalArgumentException("endAt must be in the future");
        }
        if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must not be blank and at most " + MAX_TITLE_LENGTH + " characters");
        }
        ExamSession session = new ExamSession(teacherId, examId, now);
        session.title = title;
        session.startAt = startAt;
        session.endAt = endAt;
        session.accessCode = requireValidCode(accessCode);
        session.joinOpen = true;
        session.status = SessionStatus.SCHEDULED;
        return session;
    }

    /**
     * Kiểm tra mở được không, trước khi service lấy snapshot đề. Tách riêng để kỳ thi sai trạng thái
     * báo {@code INVALID_SESSION_STATE} chứ không báo lỗi của đề.
     */
    void ensureCanOpen(Instant now) {
        requireStatus(Set.of(SessionStatus.SCHEDULED), "mở");
        if (!Objects.requireNonNull(now, "now").isBefore(endAt)) {
            throw invalidState("Kỳ thi đã quá giờ đóng, không mở được nữa");
        }
    }

    /**
     * Mở kỳ thi: chụp câu hỏi và thời lượng (ADR-0003). Giáo viên được mở sớm trước {@code startAt};
     * chỉ cần chưa quá {@code endAt}.
     */
    void open(List<SnapshotQuestion> snapshot, int durationMinutes, Instant now) {
        ensureCanOpen(now);
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("snapshot must not be empty");
        }
        if (durationMinutes < 1) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        List<SessionQuestion> copied = snapshot.stream().map(SessionQuestion::new).toList();
        questions.addAll(copied);
        this.durationMinutes = durationMinutes;
        this.status = SessionStatus.OPEN;
        this.openedAt = now;
    }

    /** Kết thúc kỳ thi đang mở; không nhận tham gia nữa. */
    void close(Instant now) {
        requireStatus(Set.of(SessionStatus.OPEN), "kết thúc");
        this.status = SessionStatus.CLOSED;
        this.joinOpen = false;
        this.closedAt = Objects.requireNonNull(now, "now");
    }

    /** Hủy kỳ thi chưa mở; mã tham gia hết hiệu lực. */
    void cancel() {
        requireStatus(Set.of(SessionStatus.SCHEDULED), "hủy");
        this.status = SessionStatus.CANCELLED;
        this.joinOpen = false;
    }

    void lockJoin() {
        requireStatus(Set.of(SessionStatus.SCHEDULED, SessionStatus.OPEN), "khóa tham gia");
        this.joinOpen = false;
    }

    void unlockJoin() {
        requireStatus(Set.of(SessionStatus.SCHEDULED, SessionStatus.OPEN), "mở lại tham gia");
        this.joinOpen = true;
    }

    boolean isOwnedBy(Long teacherId) {
        return this.teacherId.equals(teacherId);
    }

    /**
     * Còn nhận học sinh mới nhập mã không: kỳ thi chưa kết thúc ({@code SCHEDULED} hoặc
     * {@code OPEN}) và giáo viên chưa khóa tham gia (state-machines.md mục 1, ADR-0010).
     */
    boolean acceptsJoin() {
        return joinOpen && (status == SessionStatus.SCHEDULED || status == SessionStatus.OPEN);
    }

    private void requireStatus(Set<SessionStatus> allowed, String action) {
        if (!allowed.contains(status)) {
            throw invalidState("Không thể " + action + " kỳ thi đang ở trạng thái " + status);
        }
    }

    private static BusinessException invalidState(String detail) {
        return new BusinessException(ExaminationErrorCode.INVALID_SESSION_STATE, detail);
    }

    private static String requireValidCode(String code) {
        if (code == null || code.length() != ACCESS_CODE_LENGTH) {
            throw new IllegalArgumentException("accessCode must have " + ACCESS_CODE_LENGTH + " characters");
        }
        return code;
    }

    Long getId() {
        return id;
    }

    Long getExamId() {
        return examId;
    }

    Long getTeacherId() {
        return teacherId;
    }

    String getTitle() {
        return title;
    }

    String getAccessCode() {
        return accessCode;
    }

    boolean isJoinOpen() {
        return joinOpen;
    }

    Instant getStartAt() {
        return startAt;
    }

    Instant getEndAt() {
        return endAt;
    }

    Integer getDurationMinutes() {
        return durationMinutes;
    }

    SessionStatus getStatus() {
        return status;
    }

    Instant getOpenedAt() {
        return openedAt;
    }

    Instant getClosedAt() {
        return closedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    /** Bản chụp câu hỏi theo thứ tự; rỗng khi chưa mở. Không sửa được từ bên ngoài. */
    List<SessionQuestion> getQuestions() {
        return List.copyOf(questions);
    }
}
