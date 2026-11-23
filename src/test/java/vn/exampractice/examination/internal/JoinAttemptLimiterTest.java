package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Luật tạm chặn nhập mã (ADR-0010), kiểm soát thời gian hoàn toàn. */
class JoinAttemptLimiterTest {

    private static final Instant T = Instant.parse("2026-11-18T08:00:00Z");

    @Test
    void fewer_than_5_failures_never_block() {
        assertThat(JoinAttemptLimiter.blockedUntil(List.of(), T)).isEmpty();
        assertThat(JoinAttemptLimiter.blockedUntil(failures(0, 1, 2, 3), T.plusSeconds(240))).isEmpty();
    }

    @Test
    void five_failures_within_15_minutes_block_for_15_minutes_after_the_last() {
        List<Instant> fiveInTenMinutes = failures(0, 2, 5, 8, 10);
        Instant last = T.plusSeconds(600);

        assertThat(JoinAttemptLimiter.blockedUntil(fiveInTenMinutes, last.plusSeconds(1)))
                .contains(last.plusSeconds(15 * 60));
        assertThat(JoinAttemptLimiter.blockedUntil(fiveInTenMinutes, last.plusSeconds(15 * 60 - 1))).isPresent();
        assertThat(JoinAttemptLimiter.blockedUntil(fiveInTenMinutes, last.plusSeconds(15 * 60))).isEmpty();
    }

    @Test
    void five_failures_spread_over_more_than_15_minutes_do_not_block() {
        List<Instant> spread = failures(0, 5, 10, 15, 16);

        assertThat(JoinAttemptLimiter.blockedUntil(spread, T.plusSeconds(16 * 60 + 1))).isEmpty();
    }

    /** @param minutes phút của từng lần sai tính từ T, tăng dần; trả về mới nhất trước như repository */
    private static List<Instant> failures(int... minutes) {
        return java.util.Arrays.stream(minutes)
                .mapToObj(m -> T.plusSeconds(m * 60L))
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
    }
}
