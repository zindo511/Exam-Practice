package vn.exampractice.proctoring.internal;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Unit test cho {@link Presence} (ADR-0011). Không cần Spring hay database. */
class PresenceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    @Test
    void first_heartbeat_starts_without_violations() {
        Presence presence = Presence.firstSeen(12L, 5L, 9L, 3, NOW);

        assertThat(presence.getAttemptId()).isEqualTo(12L);
        assertThat(presence.getSessionId()).isEqualTo(5L);
        assertThat(presence.getStudentId()).isEqualTo(9L);
        assertThat(presence.getFirstSeenAt()).isEqualTo(NOW);
        assertThat(presence.getLastSeenAt()).isEqualTo(NOW);
        assertThat(presence.getCurrentQuestion()).isEqualTo(3);
        assertThat(presence.getViolationCount()).isZero();
    }

    @Test
    void next_heartbeat_overwrites_last_seen_and_question_but_not_first_seen() {
        Presence presence = Presence.firstSeen(12L, 5L, 9L, 3, NOW);

        presence.seen(null, NOW.plusSeconds(15));

        assertThat(presence.getFirstSeenAt()).isEqualTo(NOW);
        assertThat(presence.getLastSeenAt()).isEqualTo(NOW.plusSeconds(15));
        assertThat(presence.getCurrentQuestion()).isNull();
    }

    @Test
    void late_heartbeat_does_not_move_last_seen_backwards() {
        Presence presence = Presence.firstSeen(12L, 5L, 9L, 3, NOW);
        presence.seen(4, NOW.plusSeconds(15));

        presence.seen(1, NOW.plusSeconds(14));

        assertThat(presence.getLastSeenAt()).isEqualTo(NOW.plusSeconds(15));
        assertThat(presence.getCurrentQuestion()).isEqualTo(4);
    }

    @Test
    void question_position_starts_from_one() {
        Presence presence = Presence.firstSeen(12L, 5L, 9L, 1, NOW);

        assertThatIllegalArgumentException().isThrownBy(() -> presence.seen(0, NOW.plusSeconds(15)));
        assertThatIllegalArgumentException().isThrownBy(() -> Presence.firstSeen(12L, 5L, 9L, 0, NOW));
    }
}
