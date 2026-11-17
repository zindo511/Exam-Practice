package vn.exampractice.examdesign.internal;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một câu trong đề (bảng {@code design_exam_question}): vị trí và ID câu hỏi. Mỗi câu 1 điểm nên
 * không lưu điểm (ADR-0016).
 *
 * <p>Là thực thể con của {@link Exam} (README 6.8): không có repository riêng, chỉ được tạo trong
 * {@link Exam#replaceQuestions}. Cột {@code exam_id} do {@code @JoinColumn} phía Exam quản lý, nên
 * class này không có field trỏ ngược về đề.
 *
 * <p>{@code questionId} là ID của {@code design_question}, chỉ lưu số (ADR-0007).
 */
@Entity
@Table(name = "design_exam_question")
class ExamQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Thứ tự câu trong đề, bắt đầu từ 1. */
    @Column(nullable = false)
    private int position;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** Chỉ dành cho JPA. */
    protected ExamQuestion() {
    }

    ExamQuestion(int position, Long questionId) {
        if (position < 1) {
            throw new IllegalArgumentException("position must be at least 1");
        }
        this.position = position;
        this.questionId = Objects.requireNonNull(questionId, "questionId");
    }

    Long getId() {
        return id;
    }

    int getPosition() {
        return position;
    }

    Long getQuestionId() {
        return questionId;
    }
}
