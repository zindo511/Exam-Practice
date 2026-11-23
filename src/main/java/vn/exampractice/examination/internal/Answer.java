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
 * Đáp án học sinh chọn cho một câu (bảng {@code exam_answer}). Mỗi câu một dòng; đổi đáp án là sửa
 * dòng cũ.
 *
 * <p>Là thực thể con của {@link Attempt} (README 6.8): không có repository riêng, chỉ thay đổi qua
 * {@link Attempt#saveAnswer}.
 */
@Entity
@Table(name = "exam_answer")
class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID của {@link SessionQuestion} trong bản chụp đề của kỳ thi. */
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
        change(selectedOption, now);
    }

    void change(int selectedOption, Instant now) {
        if (selectedOption < 0) {
            throw new IllegalArgumentException("selectedOption must not be negative");
        }
        this.selectedOption = (short) selectedOption;
        this.savedAt = Objects.requireNonNull(now, "now");
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
