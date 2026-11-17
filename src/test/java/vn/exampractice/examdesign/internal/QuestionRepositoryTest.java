package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.exampractice.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra {@link Question} lưu và đọc lại đúng trên PostgreSQL thật (ADR-0014), đặc biệt danh
 * sách lựa chọn đi qua {@link QuestionOptionsConverter}.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class QuestionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");

    @Autowired
    private QuestionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void saves_and_reads_back_options_as_json() {
        Long id = repository.saveAndFlush(Question.create(7L, "Thủ đô của Việt Nam?",
                List.of("Hà Nội", "Huế", "Đà \"Nẵng\""), 0, NOW)).getId();
        entityManager.clear();

        Question found = repository.findById(id).orElseThrow();

        assertThat(found.getOptions()).containsExactly("Hà Nội", "Huế", "Đà \"Nẵng\"");
        assertThat(found.getCorrectOption()).isZero();
        assertThat(found.getTeacherId()).isEqualTo(7L);
        assertThat(found.isArchived()).isFalse();
        assertThat(jdbc.queryForObject("SELECT options_json FROM design_question WHERE id = ?", String.class, id))
                .isEqualTo("[\"Hà Nội\",\"Huế\",\"Đà \\\"Nẵng\\\"\"]");
    }

    @Test
    void update_and_archive_are_persisted() {
        Question question = repository.saveAndFlush(Question.create(7L, "Câu cũ", List.of("A", "B"), 0, NOW));

        question.update("Câu mới", List.of("C", "D", "E"), 2, NOW.plusSeconds(60));
        question.archive(NOW.plusSeconds(120));
        repository.saveAndFlush(question);
        entityManager.clear();

        Question found = repository.findById(question.getId()).orElseThrow();
        assertThat(found.getContent()).isEqualTo("Câu mới");
        assertThat(found.getOptions()).containsExactly("C", "D", "E");
        assertThat(found.getCorrectOption()).isEqualTo(2);
        assertThat(found.getArchivedAt()).isEqualTo(NOW.plusSeconds(120));
    }
}
