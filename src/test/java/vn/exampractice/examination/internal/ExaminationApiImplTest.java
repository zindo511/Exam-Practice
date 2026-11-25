package vn.exampractice.examination.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.exampractice.TestcontainersConfiguration;
import vn.exampractice.examination.ExaminationApi;
import vn.exampractice.examination.dto.AttemptRef;
import vn.exampractice.examination.dto.AttemptState;
import vn.exampractice.examination.dto.AttemptSummary;
import vn.exampractice.examination.dto.SessionAttempts;
import vn.exampractice.examination.dto.SessionState;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.support.TestAuth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ExaminationApi} trên PostgreSQL thật (ADR-0014), và câu query đóng kỳ thi chờ bài được cộng giờ
 * (ADR-0018).
 *
 * <p>Class này cố ý <b>không</b> có {@code @Transactional}: hàm lệnh chạy qua {@link ConflictRetry} và
 * phải được gọi ngoài transaction, như proctoring sẽ gọi. Dữ liệu dùng ID riêng và được xóa sau mỗi test.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@Import(TestcontainersConfiguration.class)
class ExaminationApiImplTest {

    private static final AtomicLong NEXT_ID = new AtomicLong(System.currentTimeMillis() + 12_000_000);

    @Autowired
    private ExaminationApi api;

    @Autowired
    private ExamSessionRepository sessionRepository;

    @Autowired
    private SessionParticipantRepository participantRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final long teacherId = NEXT_ID.incrementAndGet();
    private final long alice = NEXT_ID.incrementAndGet();
    private final long bob = NEXT_ID.incrementAndGet();
    private final List<Long> sessions = new ArrayList<>();

    @Test
    void session_attempts_list_every_participant_in_join_order_with_answered_count() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ExamSession session = openSession(now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(1)));
        join(session, alice, now.minusSeconds(300));
        join(session, bob, now.minusSeconds(200));
        Attempt attempt = inTransaction(() -> {
            Attempt started = Attempt.start(session.getId(), alice, 45, session.getEndAt(), now.minusSeconds(60));
            started.saveAnswer(firstQuestionId(session), 1, now.minusSeconds(30));
            return attemptRepository.save(started);
        });

        SessionAttempts result = api.getSessionAttempts(session.getId(), teacherId).orElseThrow();

        assertThat(result.status()).isEqualTo(SessionState.OPEN);
        assertThat(result.totalQuestions()).isEqualTo(2);
        assertThat(result.attempts()).extracting(AttemptSummary::studentId).containsExactly(alice, bob);
        AttemptSummary aliceRow = result.attempts().get(0);
        assertThat(aliceRow.attemptId()).isEqualTo(attempt.getId());
        assertThat(aliceRow.status()).isEqualTo(AttemptState.IN_PROGRESS);
        assertThat(aliceRow.answeredCount()).isEqualTo(1);
        assertThat(aliceRow.deadline()).isEqualTo(attempt.getDeadline());
        AttemptSummary bobRow = result.attempts().get(1);
        assertThat(bobRow.status()).isEqualTo(AttemptState.NOT_STARTED);
        assertThat(bobRow.attemptId()).isNull();
        assertThat(bobRow.answeredCount()).isZero();
    }

    @Test
    void session_attempts_of_another_teacher_are_empty() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ExamSession session = openSession(now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(1)));

        assertThat(api.getSessionAttempts(session.getId(), teacherId + 1)).isEmpty();
    }

    /** ADR-0011: học sinh không gửi heartbeat thay cho bài của người khác được. */
    @Test
    void attempt_of_student_is_found_only_for_its_owner() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ExamSession session = openSession(now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(1)));
        Attempt attempt = startAttempt(session, alice, now.minusSeconds(60));

        assertThat(api.findAttemptOfStudent(attempt.getId(), alice))
                .contains(new AttemptRef(attempt.getId(), session.getId(), alice, AttemptState.IN_PROGRESS));
        assertThat(api.findAttemptOfStudent(attempt.getId(), bob)).isEmpty();
    }

    @Test
    void extend_time_moves_the_deadline_and_bumps_the_version() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ExamSession session = openSession(now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(1)));
        Attempt attempt = startAttempt(session, alice, now.minusSeconds(60));
        long versionBefore = version(attempt.getId());

        AttemptSummary result = api.extendTime(teacherId, attempt.getId(), 10);

        assertThat(result.deadline()).isEqualTo(attempt.getDeadline().plus(Duration.ofMinutes(10)));
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getDeadline()).isEqualTo(result.deadline());
        assertThat(version(attempt.getId())).isGreaterThan(versionBefore);
    }

    @Test
    void commands_on_another_teachers_attempt_are_not_found() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ExamSession session = openSession(now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(1)));
        Attempt attempt = startAttempt(session, alice, now.minusSeconds(60));

        expect(ExaminationErrorCode.ATTEMPT_NOT_FOUND, () -> api.extendTime(teacherId + 1, attempt.getId(), 10));
        expect(ExaminationErrorCode.ATTEMPT_NOT_FOUND, () -> api.lock(teacherId + 1, attempt.getId()));
        expect(ExaminationErrorCode.ATTEMPT_NOT_FOUND, () -> api.lock(teacherId, -1L));
    }

    @Test
    void lock_ends_and_grades_the_attempt_once() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ExamSession session = openSession(now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(1)));
        long questionId = firstQuestionId(session);
        Attempt attempt = inTransaction(() -> {
            Attempt started = Attempt.start(session.getId(), alice, 45, session.getEndAt(), now.minusSeconds(60));
            started.saveAnswer(questionId, 0, now.minusSeconds(30));
            return attemptRepository.save(started);
        });

        AttemptSummary result = api.lock(teacherId, attempt.getId());

        assertThat(result.status()).isEqualTo(AttemptState.LOCKED);
        assertThat(result.submittedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT correct_count FROM exam_attempt WHERE id = ?", Integer.class,
                attempt.getId())).isEqualTo(1);
        expect(ExaminationErrorCode.ATTEMPT_LOCKED, () -> api.lock(teacherId, attempt.getId()));
        expect(ExaminationErrorCode.ATTEMPT_LOCKED, () -> api.extendTime(teacherId, attempt.getId(), 5));
    }

    /**
     * ADR-0018: kỳ thi đã quá giờ đóng nhưng còn bài được cộng giờ đang làm thì job chưa đóng; bài đó hết
     * ân hạn thì đóng.
     */
    @Test
    void session_is_not_due_to_close_while_an_extended_attempt_is_running() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant start = now.minus(Duration.ofHours(1));
        Instant end = now.minus(Duration.ofMinutes(1));
        ExamSession session = openSession(start, end);
        Attempt attempt = inTransaction(() -> {
            Attempt started = Attempt.start(session.getId(), alice, 120, end, start);
            started.extendTime(10, start);
            return attemptRepository.save(started);
        });
        Instant cutoff = now.minus(Attempt.GRACE);

        assertThat(sessionRepository.findIdsDueToClose(cutoff, Limit.of(1000))).doesNotContain(session.getId());

        Instant cutoffAfterExtension = attempt.getDeadline().plusSeconds(1);
        assertThat(sessionRepository.findIdsDueToClose(cutoffAfterExtension, Limit.of(1000))).contains(session.getId());
    }

    @AfterEach
    void cleanUp() {
        for (long session : sessions) {
            jdbc.update("DELETE FROM exam_answer WHERE attempt_id IN (SELECT id FROM exam_attempt WHERE session_id = ?)",
                    session);
            jdbc.update("DELETE FROM exam_attempt WHERE session_id = ?", session);
            jdbc.update("DELETE FROM exam_participant WHERE session_id = ?", session);
            jdbc.update("DELETE FROM exam_session_question WHERE session_id = ?", session);
            jdbc.update("DELETE FROM exam_session WHERE id = ?", session);
        }
    }

    // ---------- tiện ích ----------

    private ExamSession openSession(Instant start, Instant end) {
        ExamSession saved = inTransaction(() -> {
            ExamSession session = ExamSession.schedule(teacherId, 1L, "Kiểm tra giám sát", start, end,
                    ResultVisibility.SCORE, 10, randomCode(), start.minus(Duration.ofMinutes(5)));
            session.open(List.of(
                    new ExamSession.SnapshotQuestion(1, 9L, "Thủ đô Pháp?", List.of("Paris", "Lyon"), 0),
                    new ExamSession.SnapshotQuestion(2, 10L, "2 + 2 = ?", List.of("3", "4"), 1)), 60, start);
            return sessionRepository.save(session);
        });
        sessions.add(saved.getId());
        return saved;
    }

    private long firstQuestionId(ExamSession session) {
        return jdbc.queryForObject("SELECT id FROM exam_session_question WHERE session_id = ? AND position = 1",
                Long.class, session.getId());
    }

    private void join(ExamSession session, long studentId, Instant at) {
        inTransaction(() -> participantRepository.save(SessionParticipant.join(session.getId(), studentId, at)));
    }

    private Attempt startAttempt(ExamSession session, long studentId, Instant at) {
        return inTransaction(() -> attemptRepository.save(
                Attempt.start(session.getId(), studentId, 45, session.getEndAt(), at)));
    }

    private long version(long attemptId) {
        return jdbc.queryForObject("SELECT version FROM exam_attempt WHERE id = ?", Long.class, attemptId);
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private static String randomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return code.toString();
    }

    private static void expect(ExaminationErrorCode code, Executable action) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(code));
    }
}
