package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Chi tiết đề trả cho giáo viên: thông tin đề và danh sách câu theo thứ tự, mỗi câu kèm nội dung,
 * các lựa chọn và đáp án đúng.
 */
record ExamDetailResponse(
        Long id,
        String title,
        int durationMinutes,
        int questionCount,
        List<Item> questions,
        Instant createdAt,
        Instant updatedAt) {

    /** Một câu trong đề. {@code position} bắt đầu từ 1. */
    record Item(
            int position,
            Long questionId,
            String content,
            List<String> options,
            int correctOption) {
    }

    /**
     * @param questionsById câu hỏi của đề, lấy một lần bằng {@code findAllById}; phải có đủ mọi câu
     *                      trong đề
     */
    static ExamDetailResponse from(Exam exam, Map<Long, Question> questionsById) {
        List<Item> items = exam.getQuestions().stream()
                .map(entry -> {
                    Question question = questionsById.get(entry.getQuestionId());
                    return new Item(entry.getPosition(), question.getId(), question.getContent(),
                            question.getOptions(), question.getCorrectOption());
                })
                .toList();
        return new ExamDetailResponse(exam.getId(), exam.getTitle(), exam.getDurationMinutes(), items.size(),
                items, exam.getCreatedAt(), exam.getUpdatedAt());
    }
}
