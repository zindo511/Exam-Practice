package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.exampractice.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm tra {@link Exam} và danh sách câu ({@code @OneToMany} tới {@link ExamQuestion}) lưu và đọc
 * lại đúng trên PostgreSQL thật (ADR-0014), đặc biệt khi thay danh sách câu.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ExamRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void saves_exam_with_questions_and_reads_them_back_in_order() {
        Long q1 = question("Câu 1");
        Long q2 = question("Câu 2");
        Exam exam = Exam.create(7L, "Kiểm tra giữa kỳ", 45, NOW);
        exam.replaceQuestions(List.of(q2, q1), NOW);
        Long id = examRepository.saveAndFlush(exam).getId();
        entityManager.clear();

        Exam found = examRepository.findById(id).orElseThrow();

        assertThat(found.getTitle()).isEqualTo("Kiểm tra giữa kỳ");
        assertThat(found.getDurationMinutes()).isEqualTo(45);
        assertThat(found.getQuestions()).extracting(ExamQuestion::getQuestionId).containsExactly(q2, q1);
        assertThat(found.getQuestions()).extracting(ExamQuestion::getPosition).containsExactly(1, 2);
    }

    /**
     * Đổi chỗ hai câu: Hibernate thêm dòng mới trước khi xóa dòng cũ, nên giữa chừng có dòng trùng
     * (exam_id, position) và (exam_id, question_id). Hai ràng buộc UNIQUE kiểm tra lúc commit nên
     * không vướng. Test rollback thay vì commit, nên ép kiểm tra ngay bằng
     * {@code SET CONSTRAINTS ALL IMMEDIATE}: nếu kết quả cuối còn trùng, câu lệnh này sẽ lỗi.
     */
    @Test
    void replacing_questions_with_swapped_order_and_removal_is_persisted() {
        Long q1 = question("Câu 1");
        Long q2 = question("Câu 2");
        Long q3 = question("Câu 3");
        Exam exam = Exam.create(7L, "Đề", 15, NOW);
        exam.replaceQuestions(List.of(q1, q2, q3), NOW);
        Long id = examRepository.saveAndFlush(exam).getId();

        exam.replaceQuestions(List.of(q2, q1), NOW.plusSeconds(60));
        examRepository.saveAndFlush(exam);
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        entityManager.clear();

        Exam found = examRepository.findById(id).orElseThrow();
        assertThat(found.getQuestions()).extracting(ExamQuestion::getQuestionId).containsExactly(q2, q1);
        assertThat(found.getQuestions()).extracting(ExamQuestion::getPosition).containsExactly(1, 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM design_exam_question WHERE exam_id = ?",
                Integer.class, id)).isEqualTo(2);
    }

    /** Kiểm tra lúc commit vẫn là kiểm tra: dữ liệu cuối cùng trùng câu thì database từ chối. */
    @Test
    void database_still_rejects_the_same_question_twice_in_one_exam() {
        Long q1 = question("Câu 1");
        Exam exam = Exam.create(7L, "Đề", 15, NOW);
        exam.replaceQuestions(List.of(q1), NOW);
        Long id = examRepository.saveAndFlush(exam).getId();

        jdbc.update("INSERT INTO design_exam_question (exam_id, position, question_id) VALUES (?, 2, ?)",
                id, q1);

        assertThatThrownBy(() -> jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_design_exam_question_question");
    }

    @Test
    void archive_is_persisted_and_questions_are_kept() {
        Long q1 = question("Câu 1");
        Exam exam = Exam.create(7L, "Đề", 15, NOW);
        exam.replaceQuestions(List.of(q1), NOW);
        examRepository.saveAndFlush(exam);

        exam.archive(NOW.plusSeconds(60));
        examRepository.saveAndFlush(exam);
        entityManager.clear();

        Exam found = examRepository.findById(exam.getId()).orElseThrow();
        assertThat(found.getArchivedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(found.questionCount()).isEqualTo(1);
    }

    private Long question(String content) {
        return questionRepository.saveAndFlush(Question.create(7L, content, List.of("A", "B"), 0, NOW)).getId();
    }
}
