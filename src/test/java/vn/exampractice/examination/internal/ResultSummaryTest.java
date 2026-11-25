package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test cho {@link ResultSummary}: phần tóm tắt trên bảng điểm của giáo viên. */
class ResultSummaryTest {

    private static final Instant START = Instant.parse("2026-11-25T08:00:00Z");
    private static final Instant SESSION_END = START.plus(Duration.ofHours(2));
    /** Hai câu, thang 10: đúng 1 câu được 5 điểm, đúng 2 câu được 10 điểm. */
    private static final Grading GRADING = new Grading(Map.of(1L, 0, 2L, 1), 10);

    @Test
    void counts_each_group_and_computes_scores_from_finished_attempts_only() {
        Attempt inProgress = Attempt.start(7L, 1L, 30, SESSION_END, START);
        Attempt halfRight = submitted(2L, 0, 0);
        Attempt allRight = submitted(3L, 0, 1);

        ResultSummary summary = ResultSummary.of(4, List.of(inProgress, halfRight, allRight));

        assertThat(summary.participantCount()).isEqualTo(4);
        assertThat(summary.notStartedCount()).isEqualTo(1);
        assertThat(summary.inProgressCount()).isEqualTo(1);
        assertThat(summary.finishedCount()).isEqualTo(2);
        assertThat(summary.averageScore()).isEqualByComparingTo(new BigDecimal("7.50"));
        assertThat(summary.highestScore()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(summary.lowestScore()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void scores_are_empty_when_no_attempt_has_finished() {
        Attempt inProgress = Attempt.start(7L, 1L, 30, SESSION_END, START);

        ResultSummary summary = ResultSummary.of(3, List.of(inProgress));

        assertThat(summary.notStartedCount()).isEqualTo(2);
        assertThat(summary.inProgressCount()).isEqualTo(1);
        assertThat(summary.finishedCount()).isZero();
        assertThat(summary.averageScore()).isNull();
        assertThat(summary.highestScore()).isNull();
        assertThat(summary.lowestScore()).isNull();
    }

    @Test
    void session_without_participants_is_all_zero() {
        ResultSummary summary = ResultSummary.of(0, List.of());

        assertThat(summary).isEqualTo(new ResultSummary(0, 0, 0, 0, null, null, null));
    }

    @Test
    void average_is_rounded_to_two_decimals() {
        // 5 + 5 + 10 = 20, chia 3 = 6.666… → 6.67
        ResultSummary summary = ResultSummary.of(3,
                List.of(submitted(1L, 0, 0), submitted(2L, 0, 0), submitted(3L, 0, 1)));

        assertThat(summary.averageScore()).isEqualByComparingTo(new BigDecimal("6.67"));
    }

    private static Attempt submitted(Long studentId, int answer1, int answer2) {
        Attempt attempt = Attempt.start(7L, studentId, 30, SESSION_END, START);
        attempt.saveAnswer(1L, answer1, START.plusSeconds(60));
        attempt.saveAnswer(2L, answer2, START.plusSeconds(60));
        attempt.submit(GRADING, START.plusSeconds(120));
        return attempt;
    }
}
