package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Đề thi trong bảng {@code design_exam} (database.md mục 4): tiêu đề, thời lượng và danh sách câu
 * theo thứ tự. Mỗi câu 1 điểm, điểm bài quy về thang 10 khi chấm (ADR-0016).
 *
 * <p>Danh sách câu là {@code @OneToMany} một chiều tới {@link ExamQuestion}, đủ bốn điều kiện ở
 * README 6.8: nhỏ và có giới hạn ({@link #MAX_QUESTIONS}), câu trong đề không có nghĩa khi đứng
 * riêng, luôn tải và lưu cùng đề, {@code cascade = ALL} và {@code orphanRemoval = true}.
 *
 * <p>Trạng thái theo state-machines.md mục 3: đề đã lưu trữ không sửa được, không dùng cho kỳ thi
 * nào chưa bắt đầu.
 *
 * <p>Entity chỉ kiểm tra luật của chính nó (số câu, câu trùng). Việc câu hỏi có tồn tại, có
 * phải của giáo viên này và chưa lưu trữ hay không cần dữ liệu của Question, nên nằm ở service.
 */
@Entity
@Table(name = "design_exam")
class Exam {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_DURATION_MINUTES = 300;
    static final int MAX_QUESTIONS = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private Long teacherId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "exam_id", nullable = false)
    @OrderBy("position")
    private List<ExamQuestion> questions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Chỉ dành cho JPA. */
    protected Exam() {
    }

    private Exam(Long teacherId, Instant now) {
        this.teacherId = teacherId;
        this.createdAt = now;
    }

    /** Tạo đề mới, chưa có câu nào. Đề rỗng hợp lệ lúc soạn; kỳ thi mới cần đề có câu. */
    static Exam create(Long teacherId, String title, int durationMinutes, Instant now) {
        Objects.requireNonNull(teacherId, "teacherId");
        Objects.requireNonNull(now, "now");
        Exam exam = new Exam(teacherId, now);
        exam.applyInfo(title, durationMinutes, now);
        return exam;
    }

    /** Sửa tiêu đề và thời lượng. */
    void update(String title, int durationMinutes, Instant now) {
        requireActive();
        applyInfo(title, durationMinutes, Objects.requireNonNull(now, "now"));
    }

    /**
     * Thay toàn bộ danh sách câu. Thứ tự trong {@code questionIds} là thứ tự câu trong đề, vị trí đánh
     * lại từ 1. Danh sách rỗng nghĩa là bỏ hết câu.
     *
     * <p>Luôn sửa trên chính list đang được Hibernate theo dõi ({@code clear} rồi {@code add}),
     * không gán list mới, vì {@code orphanRemoval} cần list cũ để biết dòng nào phải xóa.
     */
    void replaceQuestions(List<Long> questionIds, Instant now) {
        requireActive();
        Objects.requireNonNull(now, "now");
        List<ExamQuestion> replacement = toExamQuestions(questionIds);
        questions.clear();
        questions.addAll(replacement);
        this.updatedAt = now;
    }

    /** Lưu trữ (xóa mềm). */
    void archive(Instant now) {
        requireActive();
        this.archivedAt = Objects.requireNonNull(now, "now");
    }

    boolean isArchived() {
        return archivedAt != null;
    }

    boolean isOwnedBy(Long teacherId) {
        return this.teacherId.equals(teacherId);
    }

    int questionCount() {
        return questions.size();
    }

    private void applyInfo(String title, int durationMinutes, Instant now) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        String trimmedTitle = title.trim();
        if (trimmedTitle.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        if (durationMinutes < 1 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("durationMinutes must be between 1 and " + MAX_DURATION_MINUTES);
        }
        this.title = trimmedTitle;
        this.durationMinutes = durationMinutes;
        this.updatedAt = now;
    }

    /** Kiểm tra hết rồi mới tạo, để danh sách sai không làm thay đổi đề. */
    private static List<ExamQuestion> toExamQuestions(List<Long> questionIds) {
        Objects.requireNonNull(questionIds, "questionIds");
        if (questionIds.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("an exam has at most " + MAX_QUESTIONS + " questions");
        }
        Set<Long> seen = new HashSet<>();
        List<ExamQuestion> result = new ArrayList<>(questionIds.size());
        for (Long questionId : questionIds) {
            if (!seen.add(Objects.requireNonNull(questionId, "questionId"))) {
                throw new IllegalArgumentException("question " + questionId + " appears more than once");
            }
            result.add(new ExamQuestion(result.size() + 1, questionId));
        }
        return result;
    }

    private void requireActive() {
        if (isArchived()) {
            throw new IllegalStateException("Exam " + id + " is archived");
        }
    }

    Long getId() {
        return id;
    }

    Long getTeacherId() {
        return teacherId;
    }

    String getTitle() {
        return title;
    }

    int getDurationMinutes() {
        return durationMinutes;
    }

    /** Danh sách theo thứ tự câu, không sửa được từ bên ngoài. */
    List<ExamQuestion> getQuestions() {
        return List.copyOf(questions);
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Instant getArchivedAt() {
        return archivedAt;
    }
}
