package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Đáp án của một câu trong bài làm (bảng {@code exam_answer}). Là thực thể con của {@link Attempt}
 * (README 6.8): không có repository riêng, chỉ được tạo, sửa và xóa qua hàm của {@link Attempt}.
 */
@Entity
@Table(name = "exam_answer")
class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID câu trong snapshot đề của kỳ thi ({@code exam_session_question}). */
    @Column(name = "session_question_id", nullable = false, updatable = false)
    private Long sessionQuestionId;

    /** Vị trí lựa chọn học sinh chọn, bắt đầu từ 0. */
    @Column(name = "selected_option", nullable = false)
    private short selectedOption;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    /** Chỉ dành cho JPA. */
    protected Answer() {
    }

    Answer(Long sessionQuestionId, int selectedOption, Instant now) {
        this.sessionQuestionId = Objects.requireNonNull(sessionQuestionId, "sessionQuestionId");
        choose(selectedOption, now);
    }

    /** Đổi lựa chọn; gọi từ {@link Attempt#saveAnswer}. */
    void choose(int selectedOption, Instant now) {
        if (selectedOption < 0) {
            throw new IllegalArgumentException("selectedOption must not be negative");
        }
        this.selectedOption = (short) selectedOption;
        this.savedAt = Objects.requireNonNull(now, "now");
    }

    Long getId() {
        return id;
    }

    Long getSessionQuestionId() {
        return sessionQuestionId;
    }

    int getSelectedOption() {
        return selectedOption;
    }

    Instant getSavedAt() {
        return savedAt;
    }
}
