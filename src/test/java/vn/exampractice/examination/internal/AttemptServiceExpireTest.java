package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit test cho {@link AttemptService#expireOverdue}: job chốt bài sau khi hết ân hạn (ADR-0013). */
@ExtendWith(MockitoExtension.class)
class AttemptServiceExpireTest {

    private static final long SESSION = 7L;
    private static final long ATTEMPT = 20L;
    private static final Instant START = Instant.parse("2026-11-23T08:00:00Z");
    private static final Instant SESSION_END = START.plus(Duration.ofHours(2));

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private ExamSessionRepository sessionRepository;

    @Mock
    private SessionParticipantRepository participantRepository;

    @Mock
    private ConflictRetry retry;

    @InjectMocks
    private AttemptService service;

    @Test
    void expires_after_grace_period_at_deadline_and_grades_saved_answers() {
        Attempt attempt = Attempt.start(SESSION, 9L, 15, SESSION_END, START);
        attempt.saveAnswer(1L, 0, START);
        when(attemptRepository.findById(ATTEMPT)).thenReturn(Optional.of(attempt));
        ExamSession session = mock(ExamSession.class);
        when(session.grading()).thenReturn(new Grading(Map.of(1L, 0, 2L, 1), 10));
        when(sessionRepository.findById(SESSION)).thenReturn(Optional.of(session));

        assertThat(service.expireOverdue(ATTEMPT, attempt.getDeadline().plusSeconds(6))).isTrue();

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(attempt.getSubmittedAt()).isEqualTo(attempt.getDeadline());
        assertThat(attempt.getScore()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    /** Trong 5 giây ân hạn học sinh vẫn còn nộp được, nên job chưa chốt. */
    @Test
    void leaves_attempt_alone_during_grace_period() {
        Attempt attempt = Attempt.start(SESSION, 9L, 15, SESSION_END, START);
        when(attemptRepository.findById(ATTEMPT)).thenReturn(Optional.of(attempt));

        assertThat(service.expireOverdue(ATTEMPT, attempt.getDeadline().plus(Attempt.GRACE))).isFalse();

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        verifyNoInteractions(sessionRepository);
    }

    /** Học sinh nộp sau khi job lấy danh sách: bài đã kết thúc thì bỏ qua. */
    @Test
    void leaves_submitted_attempt_alone() {
        Attempt attempt = Attempt.start(SESSION, 9L, 15, SESSION_END, START);
        attempt.submit(new Grading(Map.of(1L, 0), 10), START.plusSeconds(60));
        when(attemptRepository.findById(ATTEMPT)).thenReturn(Optional.of(attempt));

        assertThat(service.expireOverdue(ATTEMPT, attempt.getDeadline().plusSeconds(60))).isFalse();

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
    }

    @Test
    void missing_attempt_is_skipped() {
        when(attemptRepository.findById(ATTEMPT)).thenReturn(Optional.empty());

        assertThat(service.expireOverdue(ATTEMPT, START)).isFalse();
    }
}
