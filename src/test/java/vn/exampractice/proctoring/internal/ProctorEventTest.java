package vn.exampractice.proctoring.internal;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** Unit test cho {@link ProctorEvent}. Không cần Spring hay database. */
class ProctorEventTest {

    private static final Instant OCCURRED = Instant.parse("2026-11-25T08:00:00Z");
    private static final Instant RECEIVED = OCCURRED.plusSeconds(2);

    @Test
    void keeps_browser_time_and_server_time_apart() {
        ProctorEvent event = ProctorEvent.record(12L, 5L, 9L, ProctorEventType.TAB_HIDDEN, null, OCCURRED, RECEIVED);

        assertThat(event.getAttemptId()).isEqualTo(12L);
        assertThat(event.getSessionId()).isEqualTo(5L);
        assertThat(event.getStudentId()).isEqualTo(9L);
        assertThat(event.getType()).isEqualTo(ProctorEventType.TAB_HIDDEN);
        assertThat(event.getDetail()).isNull();
        assertThat(event.getOccurredAt()).isEqualTo(OCCURRED);
        assertThat(event.getReceivedAt()).isEqualTo(RECEIVED);
    }

    @Test
    void detail_is_at_most_500_characters() {
        String longest = "a".repeat(500);

        assertThat(ProctorEvent.record(12L, 5L, 9L, ProctorEventType.TAB_HIDDEN, longest, OCCURRED, RECEIVED)
                .getDetail()).isEqualTo(longest);
        assertThatIllegalArgumentException().isThrownBy(() -> ProctorEvent.record(12L, 5L, 9L,
                ProctorEventType.TAB_HIDDEN, longest + "a", OCCURRED, RECEIVED));
    }

    @Test
    void type_is_required() {
        assertThatNullPointerException().isThrownBy(
                () -> ProctorEvent.record(12L, 5L, 9L, null, null, OCCURRED, RECEIVED));
    }
}
