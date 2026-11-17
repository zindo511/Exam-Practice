package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import vn.exampractice.TestcontainersConfiguration;
import vn.exampractice.examdesign.ExamDesignApi;
import vn.exampractice.examdesign.dto.ExamSnapshot;
import vn.exampractice.examdesign.dto.ExamSnapshotQuestion;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test cho {@link ExamDesignApi} trên PostgreSQL thật: tóm tắt và snapshot đề, và các
 * trường hợp đề không dùng được (state-machines.md mục 1 và 3).
 *
 * <p>Gọi qua interface công khai, đúng như examination sẽ gọi.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, ExamDesignApiImpl.class})
class ExamDesignApiImplTest {

    private static final Long TEACHER = 7L;
    private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");

    @Autowired
    private ExamDesignApi api;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void summary_of_usable_exam() {
        Exam exam = exam(TEACHER, question("Câu 1"), question("Câu 2"));

        assertThat(api.getExamSummary(exam.getId(), TEACHER)).hasValueSatisfying(summary -> {
            assertThat(summary.id()).isEqualTo(exam.getId());
            assertThat(summary.title()).isEqualTo("Kiểm tra");
            assertThat(summary.durationMinutes()).isEqualTo(45);
            assertThat(summary.questionCount()).isEqualTo(2);
        });
    }

    @Test
    void snapshot_contains_questions_in_exam_order_with_correct_option() {
        Long q1 = question("Câu một");
        Long q2 = questionRepository.save(Question.create(TEACHER, "Câu hai", List.of("X", "Y", "Z"), 2, NOW)).getId();
        Exam exam = exam(TEACHER, q2, q1);
        entityManager.flush();
        entityManager.clear();

        ExamSnapshot snapshot = api.getExamSnapshot(exam.getId(), TEACHER).orElseThrow();

        assertThat(snapshot.examId()).isEqualTo(exam.getId());
        assertThat(snapshot.title()).isEqualTo("Kiểm tra");
        assertThat(snapshot.durationMinutes()).isEqualTo(45);
        assertThat(snapshot.questions()).extracting(ExamSnapshotQuestion::position).containsExactly(1, 2);
        assertThat(snapshot.questions()).extracting(ExamSnapshotQuestion::sourceQuestionId).containsExactly(q2, q1);
        ExamSnapshotQuestion first = snapshot.questions().getFirst();
        assertThat(first.content()).isEqualTo("Câu hai");
        assertThat(first.options()).containsExactly("X", "Y", "Z");
        assertThat(first.correctOption()).isEqualTo(2);
    }

    /** ADR-0003: kỳ thi chưa mở nhận được bản sửa mới nhất của câu hỏi. */
    @Test
    void snapshot_reflects_latest_question_content() {
        Long q1 = question("Câu có lỗi chính tả");
        Exam exam = exam(TEACHER, q1);
        questionRepository.findById(q1).orElseThrow().update("Câu đã sửa", List.of("A", "B"), 1, NOW);
        entityManager.flush();
        entityManager.clear();

        ExamSnapshotQuestion question = api.getExamSnapshot(exam.getId(), TEACHER).orElseThrow()
                .questions().getFirst();

        assertThat(question.content()).isEqualTo("Câu đã sửa");
        assertThat(question.correctOption()).isEqualTo(1);
    }

    @Test
    void unusable_exams_give_empty_results() {
        Long q1 = question("Câu 1");
        Exam ofOtherTeacher = exam(8L, questionOf(8L));
        Exam archived = exam(TEACHER, q1);
        archived.archive(NOW);
        Exam empty = exam(TEACHER);
        entityManager.flush();

        for (Long examId : List.of(ofOtherTeacher.getId(), archived.getId(), empty.getId(), 999_999L)) {
            assertThat(api.getExamSummary(examId, TEACHER)).as("summary of exam %d", examId).isEmpty();
            assertThat(api.getExamSnapshot(examId, TEACHER)).as("snapshot of exam %d", examId).isEmpty();
        }
    }

    private Long question(String content) {
        return questionRepository.save(Question.create(TEACHER, content, List.of("A", "B"), 0, NOW)).getId();
    }

    private Long questionOf(Long teacherId) {
        return questionRepository.save(Question.create(teacherId, "Câu", List.of("A", "B"), 0, NOW)).getId();
    }

    private Exam exam(Long teacherId, Long... questionIds) {
        Exam exam = Exam.create(teacherId, "Kiểm tra", 45, NOW);
        exam.replaceQuestions(Arrays.asList(questionIds), NOW);
        return examRepository.save(exam);
    }
}
