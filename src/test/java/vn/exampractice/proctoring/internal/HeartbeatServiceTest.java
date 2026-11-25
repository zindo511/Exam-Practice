package vn.exampractice.proctoring.internal;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit test cho {@link HeartbeatService} (ADR-0011). */
@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    private static final long ATTEMPT = 12L;
    private static final long SESSION = 5L;
    private static final long STUDENT = 9L;
    private static final long OTHER_STUDENT = 10L;

    @Mock
    private PresenceRepository presenceRepository;

    @Mock
    private ExaminationApi examinationApi;

    @InjectMocks
    private HeartbeatService service;

    @Test
    void first_heartbeat_of_own_attempt_creates_presence() {
        when(presenceRepository.findById(ATTEMPT)).thenReturn(Optional.empty());
        when(examinationApi.findAttemptOfStudent(ATTEMPT, STUDENT)).thenReturn(
                Optional.of(new AttemptRef(ATTEMPT, SESSION, STUDENT, AttemptState.IN_PROGRESS)));

        service.heartbeat(STUDENT, ATTEMPT, 3);

        ArgumentCaptor<Presence> saved = ArgumentCaptor.forClass(Presence.class);
        verify(presenceRepository).save(saved.capture());
        assertThat(saved.getValue().getAttemptId()).isEqualTo(ATTEMPT);
        assertThat(saved.getValue().getSessionId()).isEqualTo(SESSION);
        assertThat(saved.getValue().getStudentId()).isEqualTo(STUDENT);
        assertThat(saved.getValue().getCurrentQuestion()).isEqualTo(3);
    }

    @Test
    void first_heartbeat_for_attempt_of_another_student_is_not_found() {
        when(presenceRepository.findById(ATTEMPT)).thenReturn(Optional.empty());
        when(examinationApi.findAttemptOfStudent(ATTEMPT, OTHER_STUDENT)).thenReturn(Optional.empty());

        assertAttemptNotFound(OTHER_STUDENT);

        verify(presenceRepository, never()).save(any());
    }

    /** Không cần gọi save: entity đang được JPA quản lý nên tự UPDATE khi commit. */
    @Test
    void later_heartbeat_updates_presence_without_asking_examination() {
        Instant start = Instant.now().minusSeconds(15);
        Presence presence = Presence.firstSeen(ATTEMPT, SESSION, STUDENT, 3, start);
        when(presenceRepository.findById(ATTEMPT)).thenReturn(Optional.of(presence));

        service.heartbeat(STUDENT, ATTEMPT, 5);

        assertThat(presence.getFirstSeenAt()).isEqualTo(start);
        assertThat(presence.getLastSeenAt()).isAfter(start);
        assertThat(presence.getCurrentQuestion()).isEqualTo(5);
        verifyNoInteractions(examinationApi);
        verify(presenceRepository, never()).save(any());
    }

    /** Học sinh khác đổi ID trên URL để giữ bạn mình "online": không được, và dòng của bạn không đổi. */
    @Test
    void later_heartbeat_from_another_student_is_not_found_and_changes_nothing() {
        Instant start = Instant.now().minusSeconds(60);
        Presence presence = Presence.firstSeen(ATTEMPT, SESSION, STUDENT, 3, start);
        when(presenceRepository.findById(ATTEMPT)).thenReturn(Optional.of(presence));

        assertAttemptNotFound(OTHER_STUDENT);

        assertThat(presence.getLastSeenAt()).isEqualTo(start);
        assertThat(presence.getCurrentQuestion()).isEqualTo(3);
        verifyNoInteractions(examinationApi);
    }

    private void assertAttemptNotFound(long studentId) {
        assertThatThrownBy(() -> service.heartbeat(studentId, ATTEMPT, 3))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ProctoringErrorCode.ATTEMPT_NOT_FOUND));
    }
}
