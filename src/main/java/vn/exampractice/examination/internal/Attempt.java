package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.error.CommonErrorCode;

/**
 * Bài làm trong bảng {@code exam_attempt} (database.md mục 5), vòng đời theo state-machines.md mục
 * 2: {@code IN_PROGRESS → SUBMITTED | EXPIRED | LOCKED}.
 *
 * <p>Mọi thao tác ghi gọi {@link #ensureInProgress(Instant)} trước (ADR-0013): bài phải đang làm và
 * {@code now} chưa quá {@code deadline} cộng {@link #GRACE}. {@code now} luôn là giờ server, không
 * bao giờ lấy từ trình duyệt.
 *
 * <p>Đáp án là danh sách con {@link Answer} ({@code @OneToMany}, README 6.8). Sửa một đáp án có sẵn
 * không làm Hibernate tự tăng {@code version} của bài, nên service tải bài bằng khóa
 * {@code OPTIMISTIC_FORCE_INCREMENT} cho mọi thao tác ghi (state-machines.md: mọi thay đổi đều tăng
 * {@code version}).
 *
 * <p>Điểm theo ADR-0016: mỗi câu 1 điểm, quy về thang 10, làm tròn {@code HALF_UP} 2 chữ số. Bài kết
 * thúc ở trạng thái nào cũng được chấm từ các đáp án đã lưu.
 */
@Entity
@Table(name = "exam_attempt")
class Attempt {

    /** Ân hạn phía server cho lưu đáp án và nộp bài, bù độ trễ mạng sát giờ (ADR-0013). */
    static final Duration GRACE = Duration.ofSeconds(5);

    private static final BigDecimal MAX_SCORE = BigDecimal.TEN;
    private static final int SCORE_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    /** ID {@code identity_user}, không có foreign key xuyên module (ADR-0007). */
    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant deadline;

    /** Rỗng khi đang làm. */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Số câu đúng; rỗng khi đang làm. */
    @Column(name = "correct_count")
    private Integer correctCount;

    /** Thang 10, 2 chữ số thập phân; rỗng khi đang làm. */
    @Column(precision = 4, scale = 2)
    private BigDecimal score;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "attempt_id", nullable = false)
    private List<Answer> answers = new ArrayList<>();

    /** Chỉ dành cho JPA. */
    protected Attempt() {
    }

    /**
     * Bắt đầu làm bài. Kỳ thi phải đang mở và học sinh đã tham gia; service kiểm tra trước khi gọi.
     * Hạn chót là {@code min(now + thời lượng, giờ đóng kỳ thi)} (ADR-0013).
     */
    static Attempt start(Long sessionId, Long studentId, int durationMinutes, Instant sessionEndAt, Instant now) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(sessionEndAt, "sessionEndAt");
        if (durationMinutes < 1) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        if (!now.isBefore(sessionEndAt)) {
            throw new IllegalArgumentException("session has already ended");
        }
        Attempt attempt = new Attempt();
        attempt.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        attempt.studentId = Objects.requireNonNull(studentId, "studentId");
        attempt.status = AttemptStatus.IN_PROGRESS;
        attempt.startedAt = now;
        Instant byDuration = now.plus(Duration.ofMinutes(durationMinutes));
        attempt.deadline = byDuration.isBefore(sessionEndAt) ? byDuration : sessionEndAt;
        return attempt;
    }

    /**
     * Thêm hoặc đổi đáp án của một câu.
     *
     * @param optionCount số lựa chọn của câu, để kiểm tra {@code selectedOption} trỏ đúng một lựa chọn
     */
    void saveAnswer(Long sessionQuestionId, int optionCount, int selectedOption, Instant now) {
        ensureInProgress(now);
        if (selectedOption < 0 || selectedOption >= optionCount) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                    "Lựa chọn phải nằm trong khoảng từ 0 đến " + (optionCount - 1));
        }
        findAnswer(sessionQuestionId).ifPresentOrElse(
                answer -> answer.choose(selectedOption, now),
                () -> answers.add(new Answer(sessionQuestionId, selectedOption, now)));
    }

    /** Bỏ chọn một câu; câu chưa trả lời thì không làm gì. */
    void clearAnswer(Long sessionQuestionId, Instant now) {
        ensureInProgress(now);
        answers.removeIf(answer -> answer.getSessionQuestionId().equals(sessionQuestionId));
    }

    /** Học sinh nộp bài: chấm điểm, {@code submittedAt = now}. */
    void submit(AnswerKey key, Instant now) {
        ensureInProgress(now);
        finish(AttemptStatus.SUBMITTED, key, now);
    }

    /**
     * Job chốt bài hết giờ (ADR-0013): chỉ khi đã quá hạn chót cộng ân hạn, để không cắt ngang một
     * lần nộp hợp lệ sát giờ. Chấm điểm từ đáp án đã lưu, {@code submittedAt = deadline}.
     */
    void expire(AnswerKey key, Instant now) {
        requireInProgress();
        if (!isOverdue(now)) {
            throw new IllegalStateException("attempt is not overdue yet");
        }
        finish(AttemptStatus.EXPIRED, key, deadline);
    }

    /**
     * Kỳ thi đóng khi bài còn đang làm (state-machines.md mục 1). {@code submittedAt} là lúc đóng,
     * hoặc hạn chót nếu hạn chót đã qua trước đó mà job chưa kịp chốt.
     */
    void expireOnSessionClose(AnswerKey key, Instant closedAt) {
        requireInProgress();
        Objects.requireNonNull(closedAt, "closedAt");
        finish(AttemptStatus.EXPIRED, key, closedAt.isBefore(deadline) ? closedAt : deadline);
    }

    /** Đã quá hạn chót cộng ân hạn: không lưu hay nộp được nữa, chờ job chốt. */
    boolean isOverdue(Instant now) {
        return Objects.requireNonNull(now, "now").isAfter(deadline.plus(GRACE));
    }

    boolean isOwnedBy(Long studentId) {
        return this.studentId.equals(studentId);
    }

    /**
     * Bài phải đang làm và chưa quá hạn chót cộng ân hạn. Lỗi theo trạng thái: {@code SUBMITTED →
     * ATTEMPT_ALREADY_SUBMITTED}, {@code EXPIRED} hoặc quá hạn chót {@code → ATTEMPT_EXPIRED},
     * {@code LOCKED → ATTEMPT_LOCKED}.
     */
    void ensureInProgress(Instant now) {
        requireInProgress();
        if (isOverdue(now)) {
            throw new BusinessException(ExaminationErrorCode.ATTEMPT_EXPIRED);
        }
    }

    private void requireInProgress() {
        switch (status) {
            case IN_PROGRESS -> {
            }
            case SUBMITTED -> throw new BusinessException(ExaminationErrorCode.ATTEMPT_ALREADY_SUBMITTED);
            case EXPIRED -> throw new BusinessException(ExaminationErrorCode.ATTEMPT_EXPIRED);
            case LOCKED -> throw new BusinessException(ExaminationErrorCode.ATTEMPT_LOCKED);
        }
    }

    private void finish(AttemptStatus finalStatus, AnswerKey key, Instant finishedAt) {
        Objects.requireNonNull(key, "key");
        int correct = (int) answers.stream()
                .filter(answer -> key.isCorrect(answer.getSessionQuestionId(), answer.getSelectedOption()))
                .count();
        this.correctCount = correct;
        this.score = scoreOf(correct, key.questionCount());
        this.status = finalStatus;
        this.submittedAt = finishedAt;
    }

    /** ADR-0016: đúng / tổng × 10, làm tròn {@code HALF_UP} 2 chữ số; ví dụ 7/30 được 2.33. */
    static BigDecimal scoreOf(int correct, int total) {
        if (total < 1 || correct < 0 || correct > total) {
            throw new IllegalArgumentException("correct must be between 0 and total, total positive");
        }
        return MAX_SCORE.multiply(BigDecimal.valueOf(correct))
                .divide(BigDecimal.valueOf(total), SCORE_SCALE, RoundingMode.HALF_UP);
    }

    private Optional<Answer> findAnswer(Long sessionQuestionId) {
        return answers.stream().filter(answer -> answer.getSessionQuestionId().equals(sessionQuestionId)).findFirst();
    }

    Long getId() {
        return id;
    }

    Long getSessionId() {
        return sessionId;
    }

    Long getStudentId() {
        return studentId;
    }

    AttemptStatus getStatus() {
        return status;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getDeadline() {
        return deadline;
    }

    Instant getSubmittedAt() {
        return submittedAt;
    }

    Integer getCorrectCount() {
        return correctCount;
    }

    BigDecimal getScore() {
        return score;
    }

    long getVersion() {
        return version;
    }

    /** Các đáp án đã lưu. Không sửa được từ bên ngoài. */
    List<Answer> getAnswers() {
        return List.copyOf(answers);
    }
}
