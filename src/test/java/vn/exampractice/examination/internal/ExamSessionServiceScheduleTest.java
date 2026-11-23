package vn.exampractice.examination.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import vn.exampractice.examdesign.ExamDesignApi;
import vn.exampractice.examdesign.dto.ExamSnapshot;
import vn.exampractice.examdesign.dto.ExamSnapshotQuestion;
import vn.exampractice.shared.web.PageResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test cho phần của {@link ExamSessionService} mà job gọi ({@code openOnSchedule},
 * {@code closeOnSchedule}) và cờ {@code examUsable} trong danh sách kỳ thi (state-machines.md mục 1).
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionServiceScheduleTest {

    private static final long TEACHER = 1L;
    private static final long EXAM = 5L;
    private static final long SESSION = 7L;
    private static final Instant CREATED = Instant.parse("2026-11-23T06:00:00Z");
    private static final Instant START = CREATED.plus(Duration.ofHours(2));
    private static final Instant END = CREATED.plus(Duration.ofHours(4));

    @Mock
    private ExamSessionRepository repository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private ExamDesignApi examDesignApi;

    @Mock
    private AccessCodeGenerator codeGenerator;

    @Mock
    private ConflictRetry retry;

    @InjectMocks
    private ExamSessionService service;

    @Test
    void open_on_schedule_opens_with_exam_snapshot() {
        ExamSession session = scheduled(EXAM);
        when(repository.findById(SESSION)).thenReturn(Optional.of(session));
        when(examDesignApi.getExamSnapshot(EXAM, TEACHER)).thenReturn(Optional.of(new ExamSnapshot(EXAM, "Kiểm tra", 15,
                List.of(new ExamSnapshotQuestion(1, 9L, "2 + 2 = ?", List.of("3", "4"), 1)))));

        assertThat(service.openOnSchedule(SESSION, START)).isTrue();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(session.getOpenedAt()).isEqualTo(START);
        assertThat(session.getDurationMinutes()).isEqualTo(15);
    }

    /** Đề bị lưu trữ hoặc bỏ hết câu sau khi lên lịch: tới giờ mở thì hủy kỳ thi. */
    @Test
    void open_on_schedule_cancels_when_exam_is_no_longer_usable() {
        ExamSession session = scheduled(EXAM);
        when(repository.findById(SESSION)).thenReturn(Optional.of(session));
        when(examDesignApi.getExamSnapshot(EXAM, TEACHER)).thenReturn(Optional.empty());

        assertThat(service.openOnSchedule(SESSION, START)).isTrue();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(session.isJoinOpen()).isFalse();
    }

    /** Trước giờ mở job không đụng tới kỳ thi, kể cả khi đề đã hỏng. */
    @Test
    void open_on_schedule_does_nothing_before_start_time() {
        ExamSession session = scheduled(EXAM);
        when(repository.findById(SESSION)).thenReturn(Optional.of(session));

        assertThat(service.openOnSchedule(SESSION, START.minusSeconds(1))).isFalse();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        verifyNoInteractions(examDesignApi);
    }

    @Test
    void open_on_schedule_skips_session_teacher_already_cancelled() {
        ExamSession session = scheduled(EXAM);
        session.cancel();
        when(repository.findById(SESSION)).thenReturn(Optional.of(session));

        assertThat(service.openOnSchedule(SESSION, START)).isFalse();
        verifyNoInteractions(examDesignApi);
    }

    /** Job không chạy suốt khoảng giờ thi (ví dụ server tắt): kỳ thi chưa từng mở thì hủy, không CLOSED. */
    @Test
    void close_on_schedule_cancels_session_that_never_opened() {
        ExamSession session = scheduled(EXAM);
        when(repository.findById(SESSION)).thenReturn(Optional.of(session));

        assertThat(service.closeOnSchedule(SESSION, END.plusSeconds(60))).isTrue();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(session.getClosedAt()).isNull();
    }

    /**
     * Kỳ thi đang mở: đóng lúc {@code endAt}, bài còn dở được chốt và chấm. Kỳ thi là mock vì chấm điểm
     * cần ID của câu hỏi trong bản chụp, mà ID chỉ có khi lưu vào database.
     */
    @Test
    void close_on_schedule_closes_open_session_at_end_time_and_expires_remaining_attempts() {
        ExamSession session = mock(ExamSession.class);
        Instant now = END.plusSeconds(6);
        when(repository.findById(SESSION)).thenReturn(Optional.of(session));
        when(session.isDueToClose(now)).thenReturn(true);
        when(session.getStatus()).thenReturn(SessionStatus.OPEN);
        when(session.getEndAt()).thenReturn(END);
        when(session.getId()).thenReturn(SESSION);
        when(session.grading()).thenReturn(new Grading(Map.of(1L, 0), 10));
        Attempt attempt = Attempt.start(SESSION, 9L, 300, END, START);
        attempt.saveAnswer(1L, 0, START);
        when(attemptRepository.findBySessionIdAndStatus(SESSION, AttemptStatus.IN_PROGRESS)).thenReturn(List.of(attempt));

        assertThat(service.closeOnSchedule(SESSION, now)).isTrue();

        verify(session).close(END);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(attempt.getSubmittedAt()).isEqualTo(END);
        assertThat(attempt.getCorrectCount()).isEqualTo(1);
    }

    @Test
    void close_on_schedule_waits_for_grace_period() {
        ExamSession session = scheduled(EXAM);
        session.open(List.of(new ExamSession.SnapshotQuestion(1, 9L, "Câu", List.of("A", "B"), 0)), 15, START);
        when(repository.findById(SESSION)).thenReturn(Optional.of(session));

        assertThat(service.closeOnSchedule(SESSION, END.plus(Attempt.GRACE))).isFalse();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.OPEN);
    }

    /** Hỏi examdesign một lần cho cả trang; chỉ kỳ thi SCHEDULED có cờ. */
    @Test
    void list_flags_scheduled_sessions_whose_exam_is_unusable() {
        ExamSession usable = scheduled(EXAM);
        ExamSession broken = scheduled(6L);
        ExamSession open = scheduled(8L);
        open.open(List.of(new ExamSession.SnapshotQuestion(1, 9L, "Câu", List.of("A", "B"), 0)), 15, START);
        when(repository.findByTeacherId(eq(TEACHER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(usable, broken, open)));
        when(examDesignApi.findUsableExamIds(Set.of(EXAM, 6L), TEACHER)).thenReturn(Set.of(EXAM));

        PageResponse<SessionResponse> page = service.list(TEACHER, null, PageRequest.of(0, 20));

        assertThat(page.content()).extracting(SessionResponse::examUsable).containsExactly(true, false, null);
    }

    private static ExamSession scheduled(long examId) {
        return ExamSession.schedule(TEACHER, examId, "Kiểm tra", START, END, ResultVisibility.SCORE, 10, "ABC123", CREATED);
    }
}
