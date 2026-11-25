package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import vn.exampractice.identity.dto.UserSummary;

/**
 * Chi tiết một bài làm cho giáo viên: từng câu, học sinh chọn gì, đáp án đúng.
 *
 * <p>Khác {@link AttemptResponse} của học sinh: giáo viên <b>luôn</b> thấy đáp án đúng và điểm, kể cả
 * khi bài còn đang làm hay kỳ thi ẩn kết quả với học sinh.
 *
 * @param username     rỗng nếu không tìm thấy tài khoản
 * @param fullName     rỗng nếu không tìm thấy tài khoản
 * @param correctCount rỗng khi bài còn đang làm
 * @param score        rỗng khi bài còn đang làm
 */
record TeacherAttemptResponse(
        Long attemptId,
        Long sessionId,
        Long studentId,
        String username,
        String fullName,
        AttemptStatus status,
        Instant startedAt,
        Instant deadline,
        Instant submittedAt,
        int maxScore,
        Integer correctCount,
        BigDecimal score,
        int answeredCount,
        int totalQuestions,
        List<Item> questions) {

    /**
     * Một câu trong bài làm.
     *
     * @param selectedOption lựa chọn của học sinh, rỗng nếu bỏ trống
     * @param correct        {@code false} cả khi học sinh bỏ trống
     */
    record Item(
            Long questionId,
            int position,
            String content,
            List<String> options,
            Integer selectedOption,
            int correctOption,
            boolean correct) {
    }

    /** @param user {@code null} nếu không tìm thấy tài khoản */
    static TeacherAttemptResponse of(Attempt attempt, ExamSession session, UserSummary user) {
        List<Item> items = new ArrayList<>();
        for (SessionQuestion question : session.getQuestions()) {
            Integer selected = attempt.answerTo(question.getId())
                    .map(Answer::getSelectedOption)
                    .orElse(null);
            boolean correct = selected != null && selected == question.getCorrectOption();
            items.add(new Item(question.getId(), question.getPosition(), question.getContent(),
                    question.getOptions(), selected, question.getCorrectOption(), correct));
        }

        return new TeacherAttemptResponse(attempt.getId(), attempt.getSessionId(), attempt.getStudentId(),
                user == null ? null : user.username(), user == null ? null : user.fullName(),
                attempt.getStatus(), attempt.getStartedAt(), attempt.getDeadline(), attempt.getSubmittedAt(),
                session.getMaxScore(), attempt.getCorrectCount(), attempt.getScore(),
                attempt.answeredCount(), items.size(), items);
    }
}
