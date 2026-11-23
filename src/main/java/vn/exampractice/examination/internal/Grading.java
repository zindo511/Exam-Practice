package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * Cách chấm một bài làm của kỳ thi (ADR-0016): mỗi câu 1 điểm, điểm bài là
 * {@code số câu đúng / tổng số câu × maxScore}, làm tròn 2 chữ số thập phân theo {@code HALF_UP}.
 *
 * @param correctOptions đáp án đúng của từng câu trong bản chụp đề, theo ID của {@link SessionQuestion}
 * @param maxScore       thang điểm của kỳ thi
 */
record Grading(Map<Long, Integer> correctOptions, int maxScore) {

    static final int SCORE_SCALE = 2;

    Grading {
        correctOptions = Map.copyOf(Objects.requireNonNull(correctOptions, "correctOptions"));
        if (correctOptions.isEmpty()) {
            throw new IllegalArgumentException("an exam session has at least one question");
        }
    }

    int totalQuestions() {
        return correctOptions.size();
    }

    boolean isCorrect(Long sessionQuestionId, int selectedOption) {
        Integer correct = correctOptions.get(sessionQuestionId);
        return correct != null && correct == selectedOption;
    }

    /** Ví dụ thang 10, đúng 7/30 câu: 7 × 10 / 30 = 2.333… → 2.33. */
    BigDecimal score(int correctCount) {
        return BigDecimal.valueOf((long) correctCount * maxScore)
                .divide(BigDecimal.valueOf(totalQuestions()), SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
