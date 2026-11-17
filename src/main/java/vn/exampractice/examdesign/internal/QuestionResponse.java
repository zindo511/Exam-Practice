package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.List;

/**
 * Câu hỏi trả cho giáo viên, kèm đáp án đúng. Dùng cho cả xem một câu và danh sách.
 */
record QuestionResponse(
        Long id,
        String content,
        List<String> options,
        int correctOption,
        Instant createdAt,
        Instant updatedAt) {

    static QuestionResponse from(Question question) {
        return new QuestionResponse(question.getId(), question.getContent(), question.getOptions(),
                question.getCorrectOption(), question.getCreatedAt(), question.getUpdatedAt());
    }
}
