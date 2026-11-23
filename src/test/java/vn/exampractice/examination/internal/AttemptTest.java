package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.exampractice.shared.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test cho {@link Attempt} (state-machines.md mục 2, ADR-0013) và {@link Grading} (ADR-0016).
 * Không cần Spring hay database.
 */
class AttemptTest {

    private static final Instant NOW = Instant.parse("2026-11-18T08:00:00Z");
    private static final Instant SESSION_END = NOW.plus(Duration.ofHours(2));
    /** Ba câu, đáp án đúng lần lượt 0, 1, 0; thang 20. */
    private static final Grading GRADING = new Grading(Map.of(1L, 0, 2L, 1, 3L, 0), 20);

    @Test
    void deadline_is_start_plus_duration_but_never_after_session_end() {
        assertThat(Attempt.start(5L, 9L, 45, SESSION_END, NOW).getDeadline()).isEqualTo(NOW.plus(Duration.ofMinutes(45)));

        Instant lateStart = SESSION_END.minus(Duration.ofMinutes(10));
        assertThat(Attempt.start(5L, 9L, 45, SESSION_END, lateStart).getDeadline()).isEqualTo(SESSION_END);
    }

    @Test
    void new_attempt_is_in_progress_without_result() {
        Attempt attempt = Attempt.start(5L, 9L, 45, SESSION_END, NOW);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(attempt.getSubmittedAt()).isNull();
        assertThat(attempt.getScore()).isNull();
        assertThat(attempt.answeredCount()).isZero();
    }

    @Test
    void saving_an_answer_twice_changes_it_instead_of_adding() {
        Attempt attempt = Attempt.start(5L, 9L, 45, SESSION_END, NOW);

        attempt.saveAnswer(1L, 2, NOW.plusSeconds(10));
        attempt.saveAnswer(1L, 0, NOW.plusSeconds(20));

        assertThat(attempt.answeredCount()).isEqualTo(1);
        assertThat(attempt.answerTo(1L)).hasValueSatisfying(a -> {
            assertThat(a.getSelectedOption()).isZero();
            assertThat(a.getSavedAt()).isEqualTo(NOW.plusSeconds(20));
        });
    }

    /** ADR-0013: ân hạn 5 giây sau hạn chót cho độ trễ mạng. */
    @Test
    void writes_are_accepted_up_to_5_seconds_after_deadline() {
        Attempt attempt = Attempt.start(5L, 9L, 45, SESSION_END, NOW);
        Instant deadline = attempt.getDeadline();

        attempt.saveAnswer(1L, 0, deadline.plusSeconds(5));
        expect(ExaminationErrorCode.ATTEMPT_EXPIRED, () -> attempt.saveAnswer(2L, 1, deadline.plusSeconds(6)));
        expect(ExaminationErrorCode.ATTEMPT_EXPIRED, () -> attempt.submit(GRADING, deadline.plusSeconds(6)));
    }

    /**
     * Job chỉ chốt khi học sinh không còn ghi được nữa: mọi thời điểm học sinh còn nộp được thì job
     * chưa được chốt, và ngược lại (ADR-0013).
     */
    @Test
    void job_may_expire_only_after_the_grace_period_ends() {
        Attempt attempt = Attempt.start(5L, 9L, 45, SESSION_END, NOW);
        Instant deadline = attempt.getDeadline();

        assertThat(attempt.isOverdue(deadline)).isFalse();
        assertThat(attempt.isOverdue(deadline.plusSeconds(5))).isFalse();
        assertThat(attempt.isOverdue(deadline.plusSeconds(5).plusMillis(1))).isTrue();

        attempt.submit(GRADING, deadline.plusSeconds(5));
        assertThat(attempt.isOverdue(deadline.plusSeconds(60))).as("bài đã nộp").isFalse();
    }

    @Test
    void submit_grades_on_the_session_scale() {
        Attempt attempt = Attempt.start(5L, 9L, 45, SESSION_END, NOW);
        attempt.saveAnswer(1L, 0, NOW);
        attempt.saveAnswer(2L, 1, NOW);
        attempt.saveAnswer(3L, 1, NOW);

        attempt.submit(GRADING, NOW.plusSeconds(60));

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(attempt.getSubmittedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(attempt.getCorrectCount()).isEqualTo(2);
        assertThat(attempt.getScore()).isEqualTo(new BigDecimal("13.33"));
    }

    @Test
    void nothing_can_be_written_after_submit() {
        Attempt attempt = Attempt.start(5L, 9L, 45, SESSION_END, NOW);
        attempt.submit(GRADING, NOW);

        expect(ExaminationErrorCode.ATTEMPT_ALREADY_SUBMITTED, () -> attempt.saveAnswer(1L, 0, NOW));
        expect(ExaminationErrorCode.ATTEMPT_ALREADY_SUBMITTED, () -> attempt.submit(GRADING, NOW));
    }

    /** Câu bỏ trống tính là sai (ADR-0016). */
    @Test
    void expire_grades_saved_answers_and_counts_blank_as_wrong() {
        Attempt attempt = Attempt.start(5L, 9L, 45, SESSION_END, NOW);
        attempt.saveAnswer(2L, 1, NOW);

        attempt.expire(GRADING, attempt.getDeadline());

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(attempt.getSubmittedAt()).isEqualTo(attempt.getDeadline());
        assertThat(attempt.getCorrectCount()).isEqualTo(1);
        assertThat(attempt.getScore()).isEqualTo(new BigDecimal("6.67"));
        expect(ExaminationErrorCode.ATTEMPT_EXPIRED, () -> attempt.saveAnswer(1L, 0, NOW));
        assertThatThrownBy(() -> attempt.expire(GRADING, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void grading_rounds_half_up_to_2_decimals() {
        Map<Long, Integer> thirty = new java.util.HashMap<>();
        for (long id = 1; id <= 30; id++) {
            thirty.put(id, 0);
        }
        assertThat(new Grading(thirty, 10).score(7)).isEqualTo(new BigDecimal("2.33"));
        assertThat(new Grading(Map.of(1L, 0, 2L, 0, 3L, 0), 10).score(2)).isEqualTo(new BigDecimal("6.67"));
        assertThat(new Grading(Map.of(1L, 0), 100).score(1)).isEqualTo(new BigDecimal("100.00"));
        assertThat(new Grading(Map.of(1L, 0), 10).score(0)).isEqualTo(new BigDecimal("0.00"));
    }

    private static void expect(ExaminationErrorCode code, Executable action) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(code));
    }
}
