package vn.exampractice.examination.internal;

import java.math.BigDecimal;
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

/**
 * Bài làm của một học sinh trong một kỳ thi (bảng {@code exam_attempt}), vòng đời theo
 * state-machines.md mục 2: {@code IN_PROGRESS} rồi kết thúc ở {@code SUBMITTED}, {@code EXPIRED} hoặc
 * {@code LOCKED}. Cả ba trạng thái kết thúc đều đã được chấm điểm.
 *
 * <p>Hạn chót tính một lần lúc bắt đầu theo giờ server và lưu sẵn (ADR-0013). Mọi thao tác ghi kiểm
 * tra trạng thái và hạn chót, cho phép trễ {@link #GRACE 5 giây} để bù độ trễ mạng.
 *
 * <p>Đáp án là danh sách con {@code @OneToMany} ({@link Answer}, README 6.8: tối đa bằng số câu của đề).
 * {@code @Version} phát hiện thao tác ghi song song; service tải bài làm với khóa
 * {@code OPTIMISTIC_FORCE_INCREMENT} để sửa một đáp án cũng tăng version (ADR-0017).
 */
@Entity
@Table(name = "exam_attempt")
class Attempt {

    /** Ân hạn sau hạn chót cho lưu đáp án và nộp bài (ADR-0013). */
    static final Duration GRACE = Duration.ofSeconds(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant deadline;

    /** Lúc bài kết thúc: lúc nộp, hoặc hạn chót khi hết giờ; rỗng khi đang làm. */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "correct_count")
    private Integer correctCount;

    /** Điểm theo thang của kỳ thi; rỗng khi đang làm. */
    @Column(precision = 5, scale = Grading.SCORE_SCALE)
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

    private Attempt(Long sessionId, Long studentId, Instant startedAt, Instant deadline) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.startedAt = startedAt;
        this.deadline = deadline;
        this.status = AttemptStatus.IN_PROGRESS;
    }

    /**
     * Bắt đầu làm bài. Hạn chót = {@code min(now + thời lượng, giờ đóng kỳ thi)} (ADR-0013). Các điều
     * kiện cần dữ liệu khác (kỳ thi đang mở, đã tham gia, chưa có bài làm) do service kiểm tra.
     */
    static Attempt start(Long sessionId, Long studentId, int durationMinutes, Instant sessionEndAt, Instant now) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(now, "now");
        if (durationMinutes < 1) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        Instant byDuration = now.plus(Duration.ofMinutes(durationMinutes));
        Instant deadline = byDuration.isBefore(sessionEndAt) ? byDuration : sessionEndAt;
        if (!now.isBefore(deadline)) {
            throw new IllegalArgumentException("deadline must be after start");
        }
        return new Attempt(sessionId, studentId, now, deadline);
    }

    /**
     * Bài còn nhận lưu đáp án và nộp không: đang làm và chưa quá hạn chót cộng ân hạn. Báo lỗi theo
     * trạng thái (state-machines.md mục 2).
     */
    void ensureAcceptingWrites(Instant now) {
        switch (status) {
            case SUBMITTED -> throw new BusinessException(ExaminationErrorCode.ATTEMPT_ALREADY_SUBMITTED);
            case EXPIRED -> throw new BusinessException(ExaminationErrorCode.ATTEMPT_EXPIRED);
            case LOCKED -> throw new BusinessException(ExaminationErrorCode.ATTEMPT_LOCKED);
            case IN_PROGRESS -> {
                if (Objects.requireNonNull(now, "now").isAfter(deadline.plus(GRACE))) {
                    throw new BusinessException(ExaminationErrorCode.ATTEMPT_EXPIRED);
                }
            }
        }
    }

    /**
     * Lưu đáp án của một câu: chưa có thì thêm, có rồi thì sửa. Câu có thuộc kỳ thi không và lựa chọn
     * có hợp lệ không do service kiểm tra trên bản chụp đề.
     */
    void saveAnswer(Long sessionQuestionId, int selectedOption, Instant now) {
        ensureAcceptingWrites(now);
        Optional<Answer> existing = answerTo(sessionQuestionId);
        if (existing.isPresent()) {
            existing.get().change(selectedOption, now);
        } else {
            answers.add(new Answer(sessionQuestionId, selectedOption, now));
        }
    }

    /** Học sinh nộp bài: chấm ngay từ các đáp án đã lưu. */
    void submit(Grading grading, Instant now) {
        ensureAcceptingWrites(now);
        finish(AttemptStatus.SUBMITTED, grading, now);
    }

    /**
     * Job được chốt bài chưa: còn đang làm và đã quá hạn chót cộng ân hạn. Chờ hết ân hạn để job không
     * giành mất lần nộp sát giờ mà {@link #ensureAcceptingWrites} vẫn còn nhận (ADR-0013).
     */
    boolean isOverdue(Instant now) {
        return status == AttemptStatus.IN_PROGRESS && Objects.requireNonNull(now, "now").isAfter(deadline.plus(GRACE));
    }

    /**
     * Chốt bài còn đang làm khi hết giờ hoặc khi kỳ thi đóng; chấm từ các đáp án đã lưu.
     *
     * @param endedAt lúc ghi nhận bài kết thúc: hạn chót, hoặc lúc kỳ thi đóng nếu sớm hơn
     */
    void expire(Grading grading, Instant endedAt) {
        if (status != AttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("Attempt " + id + " is not in progress");
        }
        finish(AttemptStatus.EXPIRED, grading, endedAt);
    }

    private void finish(AttemptStatus finalStatus, Grading grading, Instant endedAt) {
        Objects.requireNonNull(grading, "grading");
        int correct = (int) answers.stream()
                .filter(answer -> grading.isCorrect(answer.getSessionQuestionId(), answer.getSelectedOption()))
                .count();
        this.status = finalStatus;
        this.submittedAt = Objects.requireNonNull(endedAt, "endedAt");
        this.correctCount = correct;
        this.score = grading.score(correct);
    }

    Optional<Answer> answerTo(Long sessionQuestionId) {
        return answers.stream().filter(a -> a.getSessionQuestionId().equals(sessionQuestionId)).findFirst();
    }

    int answeredCount() {
        return answers.size();
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

    /** Không sửa được từ bên ngoài. */
    List<Answer> getAnswers() {
        return List.copyOf(answers);
    }
}
