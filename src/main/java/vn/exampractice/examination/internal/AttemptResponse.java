package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Bài làm trả cho học sinh: câu hỏi, đáp án đã chọn, hạn chót, và kết quả tùy cài đặt của kỳ thi.
 *
 * <ul>
 *   <li><b>Không bao giờ</b> có đáp án đúng khi bài đang làm (README 3.3).</li>
 *   <li>{@code correctCount} và {@code score} chỉ có khi bài đã kết thúc và kỳ thi không ẩn điểm
 *       ({@link ResultVisibility}).</li>
 *   <li>{@code correctOption} và {@code correct} của từng câu chỉ có khi kỳ thi cho xem đáp án và đã
 *       đóng ({@code answersRevealed = true}).</li>
 * </ul>
 *
 * @param serverTime giờ server lúc trả response, để giao diện tính thời gian còn lại theo giờ server
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
        int answeredCount,
        int totalQuestions,
        int maxScore,
        Integer correctCount,
        BigDecimal score,
        boolean answersRevealed,
        List<Item> questions) {

    /**
     * Một câu trong bài làm.
     *
     * @param questionId     ID câu trong bản chụp đề; dùng trong {@code PUT .../answers/{questionId}}
     * @param selectedOption lựa chọn đã lưu, rỗng nếu chưa trả lời
     * @param correctOption  rỗng trừ khi {@code answersRevealed}
     * @param correct        rỗng trừ khi {@code answersRevealed}
     */
    record Item(
            Long questionId,
            int position,
            String content,
            List<String> options,
            Integer selectedOption,
            Integer correctOption,
            Boolean correct) {
    }

    static AttemptResponse of(Attempt attempt, ExamSession session, Instant now) {
        boolean finished = attempt.getStatus().isFinished();
        boolean showScore = finished && session.getResultVisibility() != ResultVisibility.HIDDEN;
        boolean showAnswers = finished && session.getResultVisibility() == ResultVisibility.SCORE_AND_ANSWERS
                && session.getStatus() == SessionStatus.CLOSED;

        List<Item> items = session.getQuestions().stream()
                .map(question -> {
                    Integer selected = attempt.answerTo(question.getId()).map(Answer::getSelectedOption).orElse(null);
                    Integer correctOption = showAnswers ? question.getCorrectOption() : null;
                    Boolean correct = showAnswers ? Integer.valueOf(question.getCorrectOption()).equals(selected) : null;
                    return new Item(question.getId(), question.getPosition(), question.getContent(),
                            question.getOptions(), selected, correctOption, correct);
                })
                .toList();

        return new AttemptResponse(attempt.getId(), attempt.getSessionId(), session.getTitle(), attempt.getStatus(),
                attempt.getStartedAt(), attempt.getDeadline(), attempt.getSubmittedAt(), now,
                attempt.answeredCount(), items.size(), session.getMaxScore(),
                showScore ? attempt.getCorrectCount() : null, showScore ? attempt.getScore() : null,
                showAnswers, items);
    }
}
