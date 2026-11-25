package vn.exampractice.proctoring.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.exampractice.examination.ExaminationApi;
import vn.exampractice.examination.dto.AttemptState;
import vn.exampractice.examination.dto.AttemptSummary;
import vn.exampractice.examination.dto.SessionAttempts;
import vn.exampractice.examination.dto.SessionState;
import vn.exampractice.identity.IdentityApi;
import vn.exampractice.identity.dto.UserSummary;
import vn.exampractice.shared.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit test cho {@link MonitorService} (ADR-0011). */
@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    private static final long TEACHER = 2L;
    private static final long SESSION = 5L;
    private static final long AN = 9L;
    private static final long BINH = 10L;
    private static final long CHI = 11L;
    private static final long AN_ATTEMPT = 12L;
    private static final long BINH_ATTEMPT = 13L;
    private static final Instant START = Instant.parse("2026-11-25T03:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-11-25T03:45:00Z");
    private static final Instant SUBMITTED = Instant.parse("2026-11-25T03:30:00Z");
    private static final Instant END = Instant.parse("2026-11-25T04:00:00Z");

    @Mock
    private ExaminationApi examinationApi;

    @Mock
    private IdentityApi identityApi;

    @Mock
    private ProctorEventRepository eventRepository;

    @InjectMocks
    private MonitorService service;

    @Captor
    private ArgumentCaptor<List<ProctorEventType>> types;

    /** An đang làm và có vi phạm, Bình đã nộp và không vi phạm, Chi chưa bắt đầu. */
    @Test
    void rows_combine_progress_names_and_violations_in_join_order() {
        givenSession(inProgress(AN, AN_ATTEMPT), submitted(BINH, BINH_ATTEMPT), notStarted(CHI));
        when(identityApi.findUsers(List.of(AN, BINH, CHI))).thenReturn(Map.of(
                AN, new UserSummary(AN, "an", "Nguyễn An"),
                BINH, new UserSummary(BINH, "binh", "Trần Bình"),
                CHI, new UserSummary(CHI, "chi", "Lê Chi")));
        when(eventRepository.countBySession(eq(SESSION), any())).thenReturn(List.of(count(AN_ATTEMPT, 3)));
        Instant before = Instant.now();

        MonitorResponse response = service.monitor(TEACHER, SESSION);

        assertThat(response.sessionId()).isEqualTo(SESSION);
        assertThat(response.title()).isEqualTo("Kiểm tra 45 phút");
        assertThat(response.status()).isEqualTo(SessionState.OPEN);
        assertThat(response.startAt()).isEqualTo(START);
        assertThat(response.endAt()).isEqualTo(END);
        assertThat(response.totalQuestions()).isEqualTo(40);
        assertThat(response.serverTime()).isBetween(before, Instant.now());
        assertThat(response.students()).containsExactly(
                new MonitorRow(AN, "an", "Nguyễn An", AN_ATTEMPT, AttemptState.IN_PROGRESS, 12, START, DEADLINE,
                        null, 3),
                new MonitorRow(BINH, "binh", "Trần Bình", BINH_ATTEMPT, AttemptState.SUBMITTED, 40, START, DEADLINE,
                        SUBMITTED, 0),
                new MonitorRow(CHI, "chi", "Lê Chi", null, AttemptState.NOT_STARTED, 0, null, null, null, 0));
    }

    @Test
    void only_leaving_tab_and_fullscreen_are_counted() {
        givenSession(inProgress(AN, AN_ATTEMPT));

        service.monitor(TEACHER, SESSION);

        verify(eventRepository).countBySession(eq(SESSION), types.capture());
        assertThat(types.getValue())
                .containsExactlyInAnyOrder(ProctorEventType.TAB_HIDDEN, ProctorEventType.FULLSCREEN_EXIT);
    }

    @Test
    void student_without_account_still_has_a_row() {
        givenSession(inProgress(AN, AN_ATTEMPT));
        when(identityApi.findUsers(List.of(AN))).thenReturn(Map.of());

        MonitorResponse response = service.monitor(TEACHER, SESSION);

        assertThat(response.students()).hasSize(1);
        MonitorRow row = response.students().get(0);
        assertThat(row.studentId()).isEqualTo(AN);
        assertThat(row.username()).isNull();
        assertThat(row.fullName()).isNull();
    }

    @Test
    void session_of_another_teacher_is_not_found() {
        when(examinationApi.getSessionAttempts(SESSION, TEACHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.monitor(TEACHER, SESSION))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProctoringErrorCode.SESSION_NOT_FOUND));

        verifyNoInteractions(identityApi, eventRepository);
    }

    private void givenSession(AttemptSummary... attempts) {
        when(examinationApi.getSessionAttempts(SESSION, TEACHER)).thenReturn(Optional.of(new SessionAttempts(SESSION,
                "Kiểm tra 45 phút", SessionState.OPEN, START, END, 40, List.of(attempts))));
    }

    private static AttemptSummary inProgress(long studentId, long attemptId) {
        return new AttemptSummary(SESSION, studentId, attemptId, AttemptState.IN_PROGRESS, 12, START, DEADLINE, null);
    }

    private static AttemptSummary submitted(long studentId, long attemptId) {
        return new AttemptSummary(SESSION, studentId, attemptId, AttemptState.SUBMITTED, 40, START, DEADLINE,
                SUBMITTED);
    }

    private static AttemptSummary notStarted(long studentId) {
        return new AttemptSummary(SESSION, studentId, null, AttemptState.NOT_STARTED, 0, null, null, null);
    }

    private static ProctorEventRepository.EventCount count(long attemptId, long total) {
        return new ProctorEventRepository.EventCount() {
            @Override
            public Long getAttemptId() {
                return attemptId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
