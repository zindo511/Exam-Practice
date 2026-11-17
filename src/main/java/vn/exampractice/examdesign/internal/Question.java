package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Câu hỏi trắc nghiệm trong bảng {@code design_question} (database.md mục 4): nhiều lựa chọn,
 * đúng một đáp án.
 *
 * <p>Trạng thái theo state-machines.md mục 3: đang dùng khi {@code archivedAt} rỗng, đã lưu trữ
 * (xóa mềm) khi có giá trị. Câu đã lưu trữ không sửa được và không lưu trữ lại.
 *
 * <p>{@code teacherId} là ID của {@code identity_user}, chỉ lưu số, không có foreign key
 * (ADR-0007).
 *
 * <p>Định dạng đầu vào đã được kiểm tra ở DTO request; các luật ở đây giữ cho entity không bao giờ
 * ở trạng thái sai, dù được gọi từ đâu.
 */
@Entity
@Table(name = "design_question")
class Question {

    static final int MIN_OPTIONS = 2;
    static final int MAX_OPTIONS = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private Long teacherId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = QuestionOptionsConverter.class)
    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private List<String> options;

    /** Vị trí đáp án đúng trong {@link #options}, bắt đầu từ 0. */
    @Column(name = "correct_option", nullable = false)
    private short correctOption;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Chỉ dành cho JPA. */
    protected Question() {
    }

    private Question(Long teacherId, Instant now) {
        this.teacherId = teacherId;
        this.createdAt = now;
    }

    /**
     * Tạo câu hỏi mới của một giáo viên.
     *
     * @param now thời điểm tạo, truyền vào để test kiểm soát được thời gian
     */
    static Question create(Long teacherId, String content, List<String> options, int correctOption, Instant now) {
        Objects.requireNonNull(teacherId, "teacherId");
        Objects.requireNonNull(now, "now");
        Question question = new Question(teacherId, now);
        question.applyContent(content, options, correctOption, now);
        return question;
    }

    /** Sửa toàn bộ nội dung câu hỏi. */
    void update(String content, List<String> options, int correctOption, Instant now) {
        requireActive();
        applyContent(content, options, correctOption, Objects.requireNonNull(now, "now"));
    }

    /**
     * Lưu trữ (xóa mềm). Việc kiểm tra "câu đang nằm trong một đề chưa lưu trữ" cần dữ liệu của
     * đề, nên nằm ở service.
     */
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

    private void applyContent(String content, List<String> options, int correctOption, Instant now) {
        String trimmedContent = requireText(content, "content").trim();
        List<String> trimmedOptions = trimOptions(options);
        if (correctOption < 0 || correctOption >= trimmedOptions.size()) {
            throw new IllegalArgumentException("correctOption must be between 0 and " + (trimmedOptions.size() - 1));
        }
        this.content = trimmedContent;
        this.options = trimmedOptions;
        this.correctOption = (short) correctOption;
        this.updatedAt = now;
    }

    private static List<String> trimOptions(List<String> options) {
        Objects.requireNonNull(options, "options");
        if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException(
                    "options must have between " + MIN_OPTIONS + " and " + MAX_OPTIONS + " items");
        }
        return options.stream()
                .map(option -> requireText(option, "option").trim())
                .toList();
    }

    private void requireActive() {
        if (isArchived()) {
            throw new IllegalStateException("Question " + id + " is archived");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    Long getId() {
        return id;
    }

    Long getTeacherId() {
        return teacherId;
    }

    String getContent() {
        return content;
    }

    /** Danh sách không sửa được. */
    List<String> getOptions() {
        return options;
    }

    int getCorrectOption() {
        return correctOption;
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
