package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho {@link ExaminationJob}: thứ tự ba bước, mốc thời gian có trừ ân hạn, và một mục lỗi không
 * làm dừng cả lượt. Repository và service đều là mock.
 */
@ExtendWith(MockitoExtension.class)
class ExaminationJobTest {

    private static final Instant NOW = Instant.parse("2026-11-23T08:00:00Z");
    private static final Instant GRACE_CUTOFF = NOW.minus(Attempt.GRACE);

    @Mock
    private ExamSessionRepository sessionRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private ExamSessionService sessionService;

    @Mock
    private AttemptService attemptService;

    @InjectMocks
    private ExaminationJob job;

    @Test
    void runs_open_expire_close_in_order_and_counts_handled_items() {
        when(sessionRepository.findIdsDueToOpen(eq(NOW), any(Limit.class))).thenReturn(List.of(1L, 2L));
        when(attemptRepository.findIdsOverdue(eq(GRACE_CUTOFF), any(Limit.class))).thenReturn(List.of(10L));
        when(sessionRepository.findIdsDueToClose(eq(GRACE_CUTOFF), any(Limit.class))).thenReturn(List.of(3L));
        when(sessionService.openOnSchedule(1L, NOW)).thenReturn(true);
        when(sessionService.openOnSchedule(2L, NOW)).thenReturn(false);
        when(attemptService.expireOverdue(10L, NOW)).thenReturn(true);
        when(sessionService.closeOnSchedule(3L, NOW)).thenReturn(true);

        ExaminationJob.Result result = job.runOnce(NOW);

        assertThat(result).isEqualTo(new ExaminationJob.Result(1, 1, 1));
        InOrder order = inOrder(sessionService, attemptService);
        order.verify(sessionService).openOnSchedule(2L, NOW);
        order.verify(attemptService).expireOverdue(10L, NOW);
        order.verify(sessionService).closeOnSchedule(3L, NOW);
    }

    /** ADR-0013: mỗi mục một transaction; xung đột hay lỗi ở một mục thì để lượt sau, các mục khác vẫn chạy. */
    @Test
    void a_failing_item_does_not_stop_the_others() {
        when(sessionRepository.findIdsDueToOpen(eq(NOW), any(Limit.class))).thenReturn(List.of(1L, 2L, 3L));
        when(sessionService.openOnSchedule(1L, NOW)).thenThrow(new OptimisticLockingFailureException("xung đột"));
        when(sessionService.openOnSchedule(2L, NOW)).thenThrow(new IllegalStateException("lỗi"));
        when(sessionService.openOnSchedule(3L, NOW)).thenReturn(true);

        ExaminationJob.Result result = job.runOnce(NOW);

        assertThat(result.opened()).isEqualTo(1);
        verify(sessionService).openOnSchedule(3L, NOW);
    }

    @Test
    void nothing_due_does_nothing() {
        ExaminationJob.Result result = job.runOnce(NOW);

        assertThat(result).isEqualTo(new ExaminationJob.Result(0, 0, 0));
        verify(sessionRepository).findIdsDueToOpen(eq(NOW), any(Limit.class));
        verify(attemptRepository).findIdsOverdue(eq(GRACE_CUTOFF), any(Limit.class));
        verify(sessionRepository).findIdsDueToClose(eq(GRACE_CUTOFF), any(Limit.class));
    }
}
