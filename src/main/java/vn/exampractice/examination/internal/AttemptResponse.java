package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bài làm trả cho học sinh: đề (không có đáp án đúng), đáp án đã chọn và thời gian. Dùng khi bắt
 * đầu, khi tải lại trang để làm tiếp (ADR-0012) và khi nộp.
 *
 * @param serverTime   giờ server lúc trả response; trình duyệt tính thời gian còn lại bằng
 *                     {@code deadline − serverTime}, không dựa vào đồng hồ máy học sinh
 * @param submittedAt  rỗng khi đang làm
 * @param correctCount rỗng khi đang làm
 * @param score        thang 10; rỗng khi đang làm
 */
record AttemptResponse(
        Long id,
        Long sessionId,
        String title,
        AttemptStatus status,
        Instant startedAt,
        Instant deadline,
        Instant submittedAt,
        Instant serverTime,
        int totalQuestions,
        int answeredCount,
        Integer correctCount,
        BigDecimal score,
        List<QuestionView> questions) {

    /**
     * Một câu như học sinh thấy. Cố ý không có {@code correctOption} (README 3.3).
     *
     * @param selectedOption rỗng khi chưa trả lời
     */
    record QuestionView(int position, String content, List<String> options, Integer selectedOption) {
    }

    static AttemptResponse from(Attempt attempt, ExamSession session, Instant now) {
        Map<Long, Integer> selected = attempt.getAnswers().stream()
                .collect(Collectors.toMap(Answer::getSessionQuestionId, Answer::getSelectedOption));
        List<QuestionView> questions = session.getQuestions().stream()
                .map(q -> new QuestionView(q.getPosition(), q.getContent(), q.getOptions(), selected.get(q.getId())))
                .toList();
        return new AttemptResponse(attempt.getId(), attempt.getSessionId(), session.getTitle(), attempt.getStatus(),
                attempt.getStartedAt(), attempt.getDeadline(), attempt.getSubmittedAt(), now, questions.size(),
                selected.size(), attempt.getCorrectCount(), attempt.getScore(), questions);
    }
}
