package vn.exampractice.examination.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.exampractice.shared.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test cho vòng đời của {@link ExamSession} theo state-machines.md mục 1. Không cần Spring hay
 * database.
 */
class ExamSessionTest {

    private static final Instant NOW = Instant.parse("2026-11-18T08:00:00Z");
    private static final Instant START = NOW.plus(Duration.ofHours(1));
    private static final Instant END = NOW.plus(Duration.ofHours(3));
    private static final List<ExamSession.SnapshotQuestion> SNAPSHOT = List.of(
            new ExamSession.SnapshotQuestion(1, 9L, "Thủ đô Pháp?", List.of("Paris", "Lyon"), 0),
            new ExamSession.SnapshotQuestion(2, 2L, "2 + 2 = ?", List.of("3", "4", "5"), 1));

    @Test
    void schedule_creates_scheduled_session_open_for_joining() {
        ExamSession session = scheduled();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(session.isJoinOpen()).isTrue();
        assertThat(session.getTitle()).isEqualTo("Kiểm tra");
        assertThat(session.getAccessCode()).isEqualTo("ABC123");
        assertThat(session.getDurationMinutes()).isNull();
        assertThat(session.getQuestions()).isEmpty();
        assertThat(session.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void schedule_requires_start_before_end_and_end_in_future() {
        assertThatThrownBy(() -> ExamSession.schedule(1L, 5L, "Kiểm tra", END, START, ResultVisibility.SCORE, 10, "ABC123", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExamSession.schedule(1L, 5L, "Kiểm tra", NOW.minusSeconds(120),
                NOW.minusSeconds(60), ResultVisibility.SCORE, 10, "ABC123", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExamSession.schedule(1L, 5L, "Kiểm tra", START, END, ResultVisibility.SCORE, 10, "ABC", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedule_keeps_result_settings_and_limits_max_score_to_1_100() {
        ExamSession session = ExamSession.schedule(1L, 5L, "Kiểm tra", START, END, ResultVisibility.HIDDEN, 20,
                "ABC123", NOW);
        assertThat(session.getResultVisibility()).isEqualTo(ResultVisibility.HIDDEN);
        assertThat(session.getMaxScore()).isEqualTo(20);

        for (int invalid : new int[] {0, 101}) {
            assertThatThrownBy(() -> ExamSession.schedule(1L, 5L, "Kiểm tra", START, END, ResultVisibility.SCORE,
                    invalid, "ABC123", NOW))
                    .as("maxScore %d", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** Giáo viên đổi được cài đặt xem kết quả ở mọi trạng thái, kể cả sau khi kỳ thi đóng. */
    @Test
    void result_visibility_can_change_in_any_state() {
        ExamSession session = scheduled();
        session.open(SNAPSHOT, 45, NOW);
        session.close(NOW.plusSeconds(60));

        session.changeResultVisibility(ResultVisibility.SCORE_AND_ANSWERS);

        assertThat(session.getResultVisibility()).isEqualTo(ResultVisibility.SCORE_AND_ANSWERS);
    }

    @Test
    void open_copies_snapshot_and_duration() {
        ExamSession session = scheduled();

        session.open(SNAPSHOT, 45, NOW);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(session.getOpenedAt()).isEqualTo(NOW);
        assertThat(session.getDurationMinutes()).isEqualTo(45);
        assertThat(session.getQuestions()).extracting(SessionQuestion::getSourceQuestionId).containsExactly(9L, 2L);
        assertThat(session.getQuestions().get(1).getCorrectOption()).isEqualTo(1);
    }

    /** Giáo viên được mở sớm trước giờ bắt đầu, chỉ cần chưa quá giờ đóng. */
    @Test
    void open_is_allowed_before_start_but_not_after_end() {
        ExamSession early = scheduled();
        early.open(SNAPSHOT, 45, START.minusSeconds(3000));
        assertThat(early.getStatus()).isEqualTo(SessionStatus.OPEN);

        ExamSession late = scheduled();
        expectInvalidState(() -> late.open(SNAPSHOT, 45, END));
        assertThat(late.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    void open_twice_is_invalid() {
        ExamSession session = scheduled();
        session.open(SNAPSHOT, 45, NOW);

        expectInvalidState(() -> session.open(SNAPSHOT, 45, NOW));
    }

    @Test
    void close_only_from_open() {
        ExamSession session = scheduled();
        expectInvalidState(() -> session.close(NOW));

        session.open(SNAPSHOT, 45, NOW);
        session.close(NOW.plusSeconds(60));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.CLOSED);
        assertThat(session.isJoinOpen()).isFalse();
        assertThat(session.getClosedAt()).isEqualTo(NOW.plusSeconds(60));
        expectInvalidState(() -> session.close(NOW));
    }

    @Test
    void cancel_only_from_scheduled() {
        ExamSession session = scheduled();
        session.cancel();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(session.isJoinOpen()).isFalse();
        expectInvalidState(session::cancel);

        ExamSession opened = scheduled();
        opened.open(SNAPSHOT, 45, NOW);
        expectInvalidState(opened::cancel);
    }

    @Test
    void join_can_be_locked_and_unlocked_while_scheduled_or_open() {
        ExamSession session = scheduled();
        session.lockJoin();
        assertThat(session.isJoinOpen()).isFalse();
        session.unlockJoin();
        assertThat(session.isJoinOpen()).isTrue();

        session.open(SNAPSHOT, 45, NOW);
        session.lockJoin();
        assertThat(session.isJoinOpen()).isFalse();
    }

    @Test
    void join_cannot_change_after_end() {
        ExamSession cancelled = scheduled();
        cancelled.cancel();

        expectInvalidState(cancelled::lockJoin);
        expectInvalidState(cancelled::unlockJoin);
        assertThat(cancelled.isJoinOpen()).isFalse();
    }

    @Test
    void accepts_join_only_while_not_ended_and_join_open() {
        ExamSession session = scheduled();
        assertThat(session.acceptsJoin()).isTrue();

        session.lockJoin();
        assertThat(session.acceptsJoin()).isFalse();
        session.unlockJoin();
        session.open(SNAPSHOT, 45, NOW);
        assertThat(session.acceptsJoin()).isTrue();

        session.close(NOW.plusSeconds(60));
        assertThat(session.acceptsJoin()).isFalse();
    }

    @Test
    void questions_cannot_be_changed_from_outside() {
        ExamSession session = scheduled();
        session.open(SNAPSHOT, 45, NOW);

        assertThatThrownBy(() -> session.getQuestions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static ExamSession scheduled() {
        return ExamSession.schedule(1L, 5L, "Kiểm tra", START, END, ResultVisibility.SCORE, 10, "ABC123", NOW);
    }

    private static void expectInvalidState(Executable action) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ExaminationErrorCode.INVALID_SESSION_STATE));
    }
}
