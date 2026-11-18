package vn.exampractice.examination.internal;

import java.time.Duration;
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
 * Kiểm tra {@link ExamSession} và bản chụp câu hỏi lưu và đọc lại đúng trên PostgreSQL thật
 * (ADR-0014).
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ExamSessionRepositoryTest {

    private static final Instant NOW = Instant.now();

    @Autowired
    private ExamSessionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void saves_open_session_with_snapshot_and_reads_it_back() {
        ExamSession session = session("ABC123");
        session.open(List.of(
                new ExamSession.SnapshotQuestion(1, 9L, "Thủ đô Pháp?", List.of("Paris", "Lyon"), 0),
                new ExamSession.SnapshotQuestion(2, 2L, "2 + 2 = ?", List.of("3", "4", "5"), 1)), 45, NOW);
        Long id = repository.saveAndFlush(session).getId();
        entityManager.clear();

        ExamSession found = repository.findById(id).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(found.getDurationMinutes()).isEqualTo(45);
        assertThat(found.getResultVisibility()).isEqualTo(ResultVisibility.SCORE_AND_ANSWERS);
        assertThat(found.getMaxScore()).isEqualTo(20);
        assertThat(found.getQuestions()).extracting(SessionQuestion::getPosition).containsExactly(1, 2);
        assertThat(found.getQuestions().get(1).getOptions()).containsExactly("3", "4", "5");
        assertThat(jdbc.queryForObject("SELECT status FROM exam_session WHERE id = ?", String.class, id))
                .isEqualTo("OPEN");
    }

    @Test
    void access_code_is_unique_across_all_sessions() {
        repository.saveAndFlush(session("ABC123"));

        assertThatThrownBy(() -> repository.saveAndFlush(session("ABC123")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void version_increases_on_every_change() {
        ExamSession session = repository.saveAndFlush(session("ABC123"));
        long before = jdbc.queryForObject("SELECT version FROM exam_session WHERE id = ?", Long.class, session.getId());

        session.lockJoin();
        repository.saveAndFlush(session);

        assertThat(jdbc.queryForObject("SELECT version FROM exam_session WHERE id = ?", Long.class, session.getId()))
                .isEqualTo(before + 1);
    }

    private static ExamSession session(String code) {
        return ExamSession.schedule(1L, 5L, "Kiểm tra", NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(3)),
                ResultVisibility.SCORE_AND_ANSWERS, 20, code, NOW);
    }
}
