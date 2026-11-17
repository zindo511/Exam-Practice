package vn.exampractice.examdesign.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.examdesign.ExamDesignApi;
import vn.exampractice.examdesign.dto.ExamSnapshot;
import vn.exampractice.examdesign.dto.ExamSnapshotQuestion;
import vn.exampractice.examdesign.dto.ExamSummary;

/**
 * Cài đặt {@link ExamDesignApi}: kiểm tra đề dùng được rồi chuyển entity sang DTO công khai. Chỉ
 * đọc, không ghi gì vào examdesign.
 */
@Service
@Transactional(readOnly = true)
class ExamDesignApiImpl implements ExamDesignApi {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;

    ExamDesignApiImpl(ExamRepository examRepository, QuestionRepository questionRepository) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
    }

    @Override
    public Optional<ExamSummary> getExamSummary(Long examId, Long teacherId) {
        return findUsable(examId, teacherId)
                .map(exam -> new ExamSummary(exam.getId(), exam.getTitle(), exam.getDurationMinutes(),
                        exam.questionCount()));
    }

    @Override
    public Optional<ExamSnapshot> getExamSnapshot(Long examId, Long teacherId) {
        return findUsable(examId, teacherId).map(this::toSnapshot);
    }

    /** Đề dùng được: của giáo viên này, chưa lưu trữ, có ít nhất 1 câu. */
    private Optional<Exam> findUsable(Long examId, Long teacherId) {
        return examRepository.findByIdAndTeacherIdAndArchivedAtIsNull(examId, teacherId)
                .filter(exam -> exam.questionCount() > 0);
    }

    /**
     * Lấy nội dung mọi câu bằng một truy vấn rồi ghép theo thứ tự trong đề. Câu trong đề chưa lưu
     * trữ luôn còn đọc được: câu hỏi chỉ xóa mềm, và không lưu trữ được khi đang nằm trong đề
     * ({@code QUESTION_IN_USE}).
     */
    private ExamSnapshot toSnapshot(Exam exam) {
        List<ExamQuestion> entries = exam.getQuestions();
        Map<Long, Question> questionsById = questionRepository
                .findAllById(entries.stream().map(ExamQuestion::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<ExamSnapshotQuestion> questions = entries.stream()
                .map(entry -> {
                    Question question = questionsById.get(entry.getQuestionId());
                    if (question == null) {
                        throw new IllegalStateException("Question " + entry.getQuestionId()
                                + " of exam " + exam.getId() + " does not exist");
                    }
                    return new ExamSnapshotQuestion(entry.getPosition(), question.getId(), question.getContent(),
                            question.getOptions(), question.getCorrectOption());
                })
                .toList();
        return new ExamSnapshot(exam.getId(), exam.getTitle(), exam.getDurationMinutes(), questions);
    }
}
