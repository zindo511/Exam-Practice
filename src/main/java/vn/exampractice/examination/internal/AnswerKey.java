package vn.exampractice.examination.internal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Đáp án đúng của một kỳ thi, lấy từ snapshot đề ({@link SessionQuestion}), dùng để chấm bài
 * (ADR-0016). Chỉ tồn tại trong bộ nhớ phía server, không bao giờ gửi xuống trình duyệt.
 *
 * @param correctOptions vị trí đáp án đúng theo ID câu trong snapshot
 */
record AnswerKey(Map<Long, Integer> correctOptions) {

    AnswerKey {
        correctOptions = Map.copyOf(Objects.requireNonNull(correctOptions, "correctOptions"));
        if (correctOptions.isEmpty()) {
            throw new IllegalArgumentException("answer key must not be empty");
        }
    }

    static AnswerKey of(List<SessionQuestion> questions) {
        return new AnswerKey(questions.stream()
                .collect(Collectors.toMap(SessionQuestion::getId, SessionQuestion::getCorrectOption)));
    }

    int questionCount() {
        return correctOptions.size();
    }

    boolean isCorrect(Long sessionQuestionId, int selectedOption) {
        Integer correct = correctOptions.get(sessionQuestionId);
        return correct != null && correct == selectedOption;
    }
}
