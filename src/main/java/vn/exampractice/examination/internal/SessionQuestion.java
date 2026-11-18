package vn.exampractice.examination.internal;

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
 * Một câu trong bản chụp đề của kỳ thi (bảng {@code exam_session_question}, ADR-0003). Chụp một lần
 * lúc mở kỳ thi và không đổi sau đó; sửa câu hỏi gốc bên examdesign không ảnh hưởng tới bản này.
 *
 * <p>Là thực thể con của {@link ExamSession} (README 6.8): chỉ được tạo trong
 * {@link ExamSession#open}. {@code correctOption} chỉ dùng để chấm, không gửi xuống trình duyệt học
 * sinh.
 */
@Entity
@Table(name = "exam_session_question")
class SessionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Thứ tự câu trong kỳ thi, bắt đầu từ 1. */
    @Column(nullable = false, updatable = false)
    private int position;

    /** ID câu hỏi gốc trong {@code design_question}, chỉ để truy vết (ADR-0007). */
    @Column(name = "source_question_id", nullable = false, updatable = false)
    private Long sourceQuestionId;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = SessionOptionsConverter.class)
    @Column(name = "options_json", nullable = false, updatable = false, columnDefinition = "TEXT")
    private List<String> options;

    /** Vị trí đáp án đúng trong {@link #options}, bắt đầu từ 0. */
    @Column(name = "correct_option", nullable = false, updatable = false)
    private short correctOption;

    /** Chỉ dành cho JPA. */
    protected SessionQuestion() {
    }

    SessionQuestion(ExamSession.SnapshotQuestion snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.position() < 1) {
            throw new IllegalArgumentException("position must be at least 1");
        }
        List<String> options = List.copyOf(snapshot.options());
        if (snapshot.correctOption() < 0 || snapshot.correctOption() >= options.size()) {
            throw new IllegalArgumentException("correctOption must point to an option");
        }
        this.position = snapshot.position();
        this.sourceQuestionId = Objects.requireNonNull(snapshot.sourceQuestionId(), "sourceQuestionId");
        this.content = Objects.requireNonNull(snapshot.content(), "content");
        this.options = options;
        this.correctOption = (short) snapshot.correctOption();
    }

    Long getId() {
        return id;
    }

    int getPosition() {
        return position;
    }

    Long getSourceQuestionId() {
        return sourceQuestionId;
    }

    String getContent() {
        return content;
    }

    List<String> getOptions() {
        return options;
    }

    int getCorrectOption() {
        return correctOption;
    }
}
