package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.error.CommonErrorCode;
import vn.exampractice.shared.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test cho vòng đời của {@link Attempt} theo state-machines.md mục 2, ân hạn và hạn chót theo
 * ADR-0013, cách chấm điểm theo ADR-0016. Không cần Spring hay database.
 */
class AttemptTest {

    private static final Instant NOW = Instant.parse("2026-11-22T08:00:00Z");
    private static final Instant SESSION_END = NOW.plus(Duration.ofHours(2));
    private static final Instant DEADLINE = NOW.plus(Duration.ofMinutes(30));

    /** Ba câu có ID 11, 12, 13; đáp án đúng lần lượt là 0, 1, 2. */
    private static final AnswerKey KEY = new AnswerKey(Map.of(11L, 0, 12L, 1, 13L, 2));

    @Test
    void start_sets_deadline_from_duration() {
        Attempt attempt = started();

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(attempt.getStartedAt()).isEqualTo(NOW);
        assertThat(attempt.getDeadline()).isEqualTo(DEADLINE);
        assertThat(attempt.getSubmittedAt()).isNull();
        assertThat(attempt.getScore()).isNull();
        assertThat(attempt.getCorrectCount()).isNull();
    }

    @Test
    void deadline_never_passes_session_end() {
        Attempt late = Attempt.start(7L, 3L, 30, NOW.plus(Duration.ofMinutes(10)), NOW);

        assertThat(late.getDeadline()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void cannot_start_after_session_end() {
        assertThatThrownBy(() -> Attempt.start(7L, 3L, 30, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void save_answer_adds_then_changes_answer_of_one_question() {
        Attempt attempt = started();

        attempt.saveAnswer(11L, 4, 2, NOW);
        attempt.saveAnswer(11L, 4, 0, NOW.plusSeconds(10));
        attempt.saveAnswer(12L, 4, 1, NOW.plusSeconds(20));

        assertThat(attempt.getAnswers()).hasSize(2);
        Answer first = attempt.getAnswers().getFirst();
        assertThat(first.getSelectedOption()).isZero();
        assertThat(first.getSavedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void save_answer_rejects_option_outside_question() {
        Attempt attempt = started();

        expectError(() -> attempt.saveAnswer(11L, 4, 4, NOW), CommonErrorCode.VALIDATION_FAILED);
        assertThat(attempt.getAnswers()).isEmpty();
    }

    @Test
    void clear_answer_removes_it() {
        Attempt attempt = started();
        attempt.saveAnswer(11L, 4, 0, NOW);

        attempt.clearAnswer(11L, NOW);
        attempt.clearAnswer(12L, NOW);

        assertThat(attempt.getAnswers()).isEmpty();
    }

    @Test
    void writes_are_accepted_within_grace_and_rejected_after() {
        Attempt attempt = started();
        Instant lastMoment = DEADLINE.plus(Attempt.GRACE);

        attempt.saveAnswer(11L, 4, 0, lastMoment);
        expectError(() -> attempt.saveAnswer(12L, 4, 1, lastMoment.plusMillis(1)), ExaminationErrorCode.ATTEMPT_EXPIRED);
        expectError(() -> attempt.submit(KEY, lastMoment.plusMillis(1)), ExaminationErrorCode.ATTEMPT_EXPIRED);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    @Test
    void submit_grades_saved_answers_on_ten_point_scale() {
        Attempt attempt = started();
        attempt.saveAnswer(11L, 4, 0, NOW);
        attempt.saveAnswer(12L, 4, 3, NOW);
        Instant submittedAt = NOW.plus(Duration.ofMinutes(12));

        attempt.submit(KEY, submittedAt);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(attempt.getSubmittedAt()).isEqualTo(submittedAt);
        assertThat(attempt.getCorrectCount()).isEqualTo(1);
        assertThat(attempt.getScore()).isEqualByComparingTo("3.33");
    }

    @Test
    void submit_with_no_answers_scores_zero() {
        Attempt attempt = started();

        attempt.submit(KEY, NOW);

        assertThat(attempt.getCorrectCount()).isZero();
        assertThat(attempt.getScore()).isEqualByComparingTo("0.00");
    }

    /** ADR-0016: ví dụ 7/30 được 2.33; làm tròn HALF_UP. */
    @Test
    void score_rounds_half_up_to_two_decimals() {
        assertThat(Attempt.scoreOf(7, 30)).isEqualTo(new BigDecimal("2.33"));
        assertThat(Attempt.scoreOf(2, 3)).isEqualTo(new BigDecimal("6.67"));
        assertThat(Attempt.scoreOf(1, 8)).isEqualTo(new BigDecimal("1.25"));
        assertThat(Attempt.scoreOf(1, 16)).isEqualTo(new BigDecimal("0.63"));
        assertThat(Attempt.scoreOf(30, 30)).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void finished_attempt_rejects_every_write_with_error_of_its_status() {
        Attempt submitted = started();
        submitted.submit(KEY, NOW);
        expectError(() -> submitted.saveAnswer(11L, 4, 0, NOW), ExaminationErrorCode.ATTEMPT_ALREADY_SUBMITTED);
        expectError(() -> submitted.clearAnswer(11L, NOW), ExaminationErrorCode.ATTEMPT_ALREADY_SUBMITTED);
        expectError(() -> submitted.submit(KEY, NOW), ExaminationErrorCode.ATTEMPT_ALREADY_SUBMITTED);

        Attempt expired = started();
        expired.expireOnSessionClose(KEY, NOW);
        expectError(() -> expired.saveAnswer(11L, 4, 0, NOW), ExaminationErrorCode.ATTEMPT_EXPIRED);
        expectError(() -> expired.submit(KEY, NOW), ExaminationErrorCode.ATTEMPT_EXPIRED);
    }

    @Test
    void expire_requires_deadline_plus_grace_to_have_passed() {
        Attempt attempt = started();
        attempt.saveAnswer(13L, 4, 2, NOW);

        assertThatThrownBy(() -> attempt.expire(KEY, DEADLINE.plus(Attempt.GRACE)))
                .isInstanceOf(IllegalStateException.class);

        attempt.expire(KEY, DEADLINE.plus(Duration.ofMinutes(1)));
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(attempt.getSubmittedAt()).isEqualTo(DEADLINE);
        assertThat(attempt.getCorrectCount()).isEqualTo(1);
        assertThat(attempt.getScore()).isEqualByComparingTo("3.33");
    }

    @Test
    void expire_on_session_close_uses_close_time_or_earlier_deadline() {
        Attempt early = started();
        Instant closedAt = NOW.plus(Duration.ofMinutes(5));
        early.expireOnSessionClose(KEY, closedAt);
        assertThat(early.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(early.getSubmittedAt()).isEqualTo(closedAt);
        assertThat(early.getScore()).isEqualByComparingTo("0.00");

        Attempt overdue = started();
        overdue.expireOnSessionClose(KEY, DEADLINE.plus(Duration.ofMinutes(5)));
        assertThat(overdue.getSubmittedAt()).isEqualTo(DEADLINE);
    }

    @Test
    void ownership() {
        Attempt attempt = started();

        assertThat(attempt.isOwnedBy(3L)).isTrue();
        assertThat(attempt.isOwnedBy(4L)).isFalse();
    }

    private static Attempt started() {
        return Attempt.start(7L, 3L, 30, SESSION_END, NOW);
    }

    private static void expectError(Executable action, ErrorCode code) {
        assertThatThrownBy(action::execute)
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
}
