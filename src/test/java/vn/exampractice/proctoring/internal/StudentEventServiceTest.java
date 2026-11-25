package vn.exampractice.proctoring.internal;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.exampractice.examination.ExaminationApi;
import vn.exampractice.examination.dto.AttemptRef;
import vn.exampractice.examination.dto.AttemptState;
import vn.exampractice.shared.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit test cho {@link StudentEventService} (ADR-0019). */
@ExtendWith(MockitoExtension.class)
class StudentEventServiceTest {

    private static final long ATTEMPT = 12L;
    private static final long SESSION = 5L;
    private static final long STUDENT = 9L;
    private static final long OTHER_STUDENT = 10L;
    private static final Instant OCCURRED = Instant.parse("2026-11-25T03:15:20Z");

    @Mock
    private ProctorEventRepository eventRepository;

    @Mock
    private ExaminationApi examinationApi;

    @InjectMocks
    private StudentEventService service;

    @ParameterizedTest
    @EnumSource(BrowserEventType.class)
    void event_of_own_attempt_in_progress_is_saved(BrowserEventType type) {
        givenAttempt(AttemptState.IN_PROGRESS);
        Instant before = Instant.now();

        service.report(STUDENT, ATTEMPT, type, OCCURRED);

        ArgumentCaptor<ProctorEvent> captor = ArgumentCaptor.forClass(ProctorEvent.class);
        verify(eventRepository).save(captor.capture());
        ProctorEvent saved = captor.getValue();
        assertThat(saved.getAttemptId()).isEqualTo(ATTEMPT);
        assertThat(saved.getSessionId()).isEqualTo(SESSION);
        assertThat(saved.getStudentId()).isEqualTo(STUDENT);
        assertThat(saved.getType().name()).isEqualTo(type.name());
        assertThat(saved.getDetail()).isNull();
        assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED);
        assertThat(saved.getReceivedAt()).isBetween(before, Instant.now());
    }

    @Test
    void attempt_of_another_student_is_not_found() {
        when(examinationApi.findAttemptOfStudent(ATTEMPT, OTHER_STUDENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report(OTHER_STUDENT, ATTEMPT, BrowserEventType.TAB_HIDDEN, OCCURRED))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProctoringErrorCode.ATTEMPT_NOT_FOUND));

        verify(eventRepository, never()).save(any());
    }

    /** Sự kiện đến sau khi bài kết thúc thì bỏ qua, không báo lỗi. */
    @ParameterizedTest
    @EnumSource(value = AttemptState.class, mode = EnumSource.Mode.EXCLUDE, names = "IN_PROGRESS")
    void event_of_attempt_not_in_progress_is_ignored(AttemptState state) {
        givenAttempt(state);

        service.report(STUDENT, ATTEMPT, BrowserEventType.TAB_HIDDEN, OCCURRED);

        verify(eventRepository, never()).save(any());
    }

    private void givenAttempt(AttemptState state) {
        when(examinationApi.findAttemptOfStudent(ATTEMPT, STUDENT))
                .thenReturn(Optional.of(new AttemptRef(ATTEMPT, SESSION, STUDENT, state)));
    }
}
