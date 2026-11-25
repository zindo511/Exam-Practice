package vn.exampractice.proctoring.internal;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test cho {@link ProctorEventType} (database.md mục 6). */
class ProctorEventTypeTest {

    @Test
    void only_leaving_tab_and_fullscreen_are_violations() {
        assertThat(Arrays.stream(ProctorEventType.values()).filter(ProctorEventType::isViolation))
                .containsExactlyInAnyOrder(ProctorEventType.TAB_HIDDEN, ProctorEventType.FULLSCREEN_EXIT);
    }

    @Test
    void browser_cannot_report_server_or_teacher_events() {
        assertThat(Arrays.stream(ProctorEventType.values()).filter(ProctorEventType::isReportedByBrowser))
                .containsExactlyInAnyOrder(ProctorEventType.TAB_HIDDEN, ProctorEventType.TAB_VISIBLE,
                        ProctorEventType.FULLSCREEN_EXIT);
    }
}
