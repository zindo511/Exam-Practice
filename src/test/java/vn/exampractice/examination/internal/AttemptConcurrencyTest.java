package vn.exampractice.examination.internal;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.exampractice.TestcontainersConfiguration;
import vn.exampractice.support.TestAuth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Optimistic locking của {@link Attempt} (ADR-0013) với transaction commit thật: mỗi thao tác ghi,
 * kể cả đổi một đáp án có sẵn, tăng {@code version}, nên hai thao tác chồng nhau thì bên commit sau
 * bị từ chối.
 *
 * <p>Không chạy trong transaction rollback như các test khác, nên dữ liệu được tạo bằng SQL với ID
 * riêng và xóa sau mỗi test.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@Import(TestcontainersConfiguration.class)
class AttemptConcurrencyTest {

    private static final long TEACHER_ID = 9_001L;
    private static final long STUDENT_ID = 9_002L;

    @Autowired
    private AttemptService attemptService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long sessionId;
    private long attemptId;

    @BeforeEach
    void openSessionAndStart() {
        Instant now = Instant.now();
        sessionId = jdbc.queryForObject("""
                INSERT INTO exam_session (exam_id, teacher_id, title, access_code, join_open, start_at, end_at,
                                          duration_minutes, status, opened_at, created_at, version)
                VALUES (1, ?, 'Kỳ thi', 'LOCK01', true, ?, ?, 30, 'OPEN', ?, ?, 0) RETURNING id""",
                Long.class, TEACHER_ID, Timestamp.from(now), Timestamp.from(now.plus(Duration.ofHours(2))),
                Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
                INSERT INTO exam_session_question (session_id, position, source_question_id, content, options_json,
                                                   correct_option)
                VALUES (?, 1, 1, 'Câu 1', '["A","B"]', 0)""", sessionId);
        jdbc.update("INSERT INTO exam_participant (session_id, student_id, joined_at) VALUES (?, ?, ?)",
                sessionId, STUDENT_ID, Timestamp.from(now));
        attemptId = attemptService.start(STUDENT_ID, sessionId).id();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM exam_answer WHERE attempt_id IN (SELECT id FROM exam_attempt WHERE session_id = ?)",
                sessionId);
        jdbc.update("DELETE FROM exam_attempt WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM exam_participant WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM exam_session_question WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM exam_session WHERE id = ?", sessionId);
    }

    /** state-machines.md mục 2: mọi thay đổi đều tăng version, kể cả đổi một đáp án có sẵn. */
    @Test
    void every_answer_change_increments_version() {
        long started = version();
        attemptService.saveAnswer(STUDENT_ID, attemptId, 1, 0);
        long added = version();
        attemptService.saveAnswer(STUDENT_ID, attemptId, 1, 1);
        long changed = version();
        attemptService.clearAnswer(STUDENT_ID, attemptId, 1);

        // Thêm hoặc xóa đáp án có thể tăng hai lần (danh sách con đổi và khóa FORCE_INCREMENT); chỉ
        // cần mỗi thao tác đều tăng.
        assertThat(added).isGreaterThan(started);
        assertThat(changed).isGreaterThan(added);
        assertThat(version()).isGreaterThan(changed);
    }

    /** Học sinh lưu đáp án trong khi một thao tác khác vừa commit: bên commit sau bị từ chối. */
    @Test
    void overlapping_writes_are_rejected() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            attemptService.saveAnswer(STUDENT_ID, attemptId, 1, 0);
            inner.executeWithoutResult(s -> attemptService.submit(STUDENT_ID, attemptId));
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(jdbc.queryForObject("SELECT status FROM exam_attempt WHERE id = ?", String.class, attemptId))
                .isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_answer WHERE attempt_id = ?", Integer.class,
                attemptId)).isZero();
    }

    private long version() {
        return jdbc.queryForObject("SELECT version FROM exam_attempt WHERE id = ?", Long.class, attemptId);
    }
}
