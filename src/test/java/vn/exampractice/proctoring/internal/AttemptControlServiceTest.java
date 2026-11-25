package vn.exampractice.proctoring.internal;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.exampractice.examination.ExaminationApi;
import vn.exampractice.examination.dto.AttemptState;
import vn.exampractice.examination.dto.AttemptSummary;
import vn.exampractice.shared.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit test cho {@link AttemptControlService} (ADR-0012). */
@ExtendWith(MockitoExtension.class)
class AttemptControlServiceTest {

    private static final long TEACHER = 2L;
    private static final long ATTEMPT = 12L;
    private static final long SESSION = 5L;
    private static final long STUDENT = 9L;
    private static final Instant STARTED = Instant.parse("2026-11-25T03:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-11-25T03:40:00Z");
    private static final Instant LOCKED_AT = Instant.parse("2026-11-25T03:20:00Z");

    @Mock
    private ExaminationApi examinationApi;

    @Mock
    private ProctorEventRepository eventRepository;

    @InjectMocks
    private AttemptControlService service;

    @Test
    void extend_time_returns_new_deadline_and_saves_event_with_minutes() {
        when(examinationApi.extendTime(TEACHER, ATTEMPT, 10)).thenReturn(attempt(AttemptState.IN_PROGRESS, null));

        AttemptControlResponse response = service.extendTime(TEACHER, ATTEMPT, 10);

        assertThat(response).isEqualTo(
                new AttemptControlResponse(ATTEMPT, STUDENT, AttemptState.IN_PROGRESS, DEADLINE, null));
        ProctorEvent saved = savedEvent();
        assertBelongsToAttempt(saved);
        assertThat(saved.getType()).isEqualTo(ProctorEventType.TEACHER_EXTENDED_TIME);
        assertThat(saved.getDetail()).isEqualTo("Cộng 10 phút");
        assertThat(saved.getOccurredAt()).isEqualTo(saved.getReceivedAt());
    }

    @Test
    void lock_returns_locked_attempt_and_saves_event() {
        when(examinationApi.lock(TEACHER, ATTEMPT)).thenReturn(attempt(AttemptState.LOCKED, LOCKED_AT));

        AttemptControlResponse response = service.lock(TEACHER, ATTEMPT);

        assertThat(response).isEqualTo(
                new AttemptControlResponse(ATTEMPT, STUDENT, AttemptState.LOCKED, DEADLINE, LOCKED_AT));
        ProctorEvent saved = savedEvent();
        assertBelongsToAttempt(saved);
        assertThat(saved.getType()).isEqualTo(ProctorEventType.TEACHER_LOCKED);
        assertThat(saved.getDetail()).isNull();
    }

    @Test
    void refused_extend_time_saves_nothing() {
        BusinessException refused = new BusinessException(ProctoringErrorCode.ATTEMPT_NOT_FOUND);
        when(examinationApi.extendTime(TEACHER, ATTEMPT, 10)).thenThrow(refused);

        assertThatThrownBy(() -> service.extendTime(TEACHER, ATTEMPT, 10)).isSameAs(refused);

        verifyNoInteractions(eventRepository);
    }

    @Test
    void refused_lock_saves_nothing() {
        BusinessException refused = new BusinessException(ProctoringErrorCode.ATTEMPT_NOT_FOUND);
        when(examinationApi.lock(TEACHER, ATTEMPT)).thenThrow(refused);

        assertThatThrownBy(() -> service.lock(TEACHER, ATTEMPT)).isSameAs(refused);

        verifyNoInteractions(eventRepository);
    }

    private static AttemptSummary attempt(AttemptState state, Instant submittedAt) {
        return new AttemptSummary(SESSION, STUDENT, ATTEMPT, state, 3, STARTED, DEADLINE, submittedAt);
    }

    private ProctorEvent savedEvent() {
        ArgumentCaptor<ProctorEvent> captor = ArgumentCaptor.forClass(ProctorEvent.class);
        verify(eventRepository).save(captor.capture());
        return captor.getValue();
    }

    private static void assertBelongsToAttempt(ProctorEvent event) {
        assertThat(event.getAttemptId()).isEqualTo(ATTEMPT);
        assertThat(event.getSessionId()).isEqualTo(SESSION);
        assertThat(event.getStudentId()).isEqualTo(STUDENT);
    }
}
