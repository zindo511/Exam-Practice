package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import vn.exampractice.identity.IdentityApi;
import vn.exampractice.identity.dto.UserSummary;
import vn.exampractice.shared.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Unit test cho {@link SessionResultService}: bảng điểm và chi tiết bài làm của giáo viên. */
@ExtendWith(MockitoExtension.class)
class SessionResultServiceTest {

    private static final long TEACHER = 1L;
    private static final long SESSION = 7L;
    private static final long ATTEMPT = 20L;
    private static final long ALICE = 11L;
    private static final long BOB = 12L;
    private static final Instant START = Instant.parse("2026-11-25T08:00:00Z");
    private static final Instant SESSION_END = START.plus(Duration.ofHours(2));
    private static final Grading GRADING = new Grading(Map.of(1L, 0, 2L, 1), 10);
    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @Mock
    private ExamSessionRepository sessionRepository;

    @Mock
    private SessionParticipantRepository participantRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private IdentityApi identityApi;

    @InjectMocks
    private SessionResultService service;

    @Test
    void results_list_every_participant_including_those_who_have_not_started() {
        ExamSession session = openSession(ResultVisibility.HIDDEN);
        when(sessionRepository.findByIdAndTeacherId(SESSION, TEACHER)).thenReturn(Optional.of(session));
        SessionParticipant alice = SessionParticipant.join(SESSION, ALICE, START);
        SessionParticipant bob = SessionParticipant.join(SESSION, BOB, START.plusSeconds(30));
        when(participantRepository.findBySessionId(any(), any())).thenReturn(new PageImpl<>(List.of(alice, bob)));
        when(participantRepository.countBySessionId(SESSION)).thenReturn(2L);
        Attempt aliceAttempt = submittedAttempt(ALICE);
        when(attemptRepository.findBySessionIdAndStudentIdIn(SESSION, List.of(ALICE, BOB)))
                .thenReturn(List.of(aliceAttempt));
        when(attemptRepository.findBySessionId(SESSION)).thenReturn(List.of(aliceAttempt));
        when(identityApi.findUsers(List.of(ALICE, BOB))).thenReturn(Map.of(
                ALICE, new UserSummary(ALICE, "alice", "Nguyễn Alice"),
                BOB, new UserSummary(BOB, "bob", "Trần Bob")));

        SessionResultsResponse response = service.results(TEACHER, SESSION, FIRST_PAGE);

        assertThat(response.totalQuestions()).isEqualTo(2);
        assertThat(response.students().content()).hasSize(2);

        StudentResultRow aliceRow = response.students().content().get(0);
        assertThat(aliceRow.fullName()).isEqualTo("Nguyễn Alice");
        assertThat(aliceRow.attemptStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        // Kỳ thi ẩn điểm với học sinh, nhưng giáo viên vẫn thấy.
        assertThat(aliceRow.score()).isEqualByComparingTo(new BigDecimal("5.00"));

        StudentResultRow bobRow = response.students().content().get(1);
        assertThat(bobRow.fullName()).isEqualTo("Trần Bob");
        assertThat(bobRow.attemptStatus()).isNull();
        assertThat(bobRow.score()).isNull();

        assertThat(response.summary().participantCount()).isEqualTo(2);
        assertThat(response.summary().notStartedCount()).isEqualTo(1);
        assertThat(response.summary().finishedCount()).isEqualTo(1);
    }

    @Test
    void results_of_a_session_without_participants_do_not_ask_identity() {
        ExamSession session = openSession(ResultVisibility.SCORE);
        when(sessionRepository.findByIdAndTeacherId(SESSION, TEACHER)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(any(), any())).thenReturn(new PageImpl<>(List.of()));

        SessionResultsResponse response = service.results(TEACHER, SESSION, FIRST_PAGE);

        assertThat(response.students().content()).isEmpty();
        assertThat(response.summary().participantCount()).isZero();
        verifyNoInteractions(identityApi);
    }

    @Test
    void results_of_another_teachers_session_are_not_found() {
        when(sessionRepository.findByIdAndTeacherId(SESSION, TEACHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.results(TEACHER, SESSION, FIRST_PAGE))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ExaminationErrorCode.SESSION_NOT_FOUND));
        verifyNoInteractions(participantRepository, attemptRepository, identityApi);
    }

    @Test
    void attempt_shows_selected_and_correct_option_of_every_question() {
        ExamSession session = openSession(ResultVisibility.HIDDEN);
        when(sessionRepository.findByIdAndTeacherId(SESSION, TEACHER)).thenReturn(Optional.of(session));
        Attempt attempt = submittedAttempt(ALICE);
        when(attemptRepository.findByIdAndSessionId(ATTEMPT, SESSION)).thenReturn(Optional.of(attempt));
        when(identityApi.findUsers(List.of(ALICE)))
                .thenReturn(Map.of(ALICE, new UserSummary(ALICE, "alice", "Nguyễn Alice")));

        TeacherAttemptResponse response = service.attempt(TEACHER, SESSION, ATTEMPT);

        assertThat(response.fullName()).isEqualTo("Nguyễn Alice");
        assertThat(response.score()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(response.answeredCount()).isEqualTo(1);
        assertThat(response.questions()).containsExactly(
                new TeacherAttemptResponse.Item(1L, 1, "Câu 1", List.of("A", "B"), 0, 0, true),
                new TeacherAttemptResponse.Item(2L, 2, "Câu 2", List.of("A", "B"), null, 1, false));
    }

    @Test
    void attempt_of_another_session_is_not_found() {
        ExamSession session = mock(ExamSession.class);
        when(sessionRepository.findByIdAndTeacherId(SESSION, TEACHER)).thenReturn(Optional.of(session));
        when(attemptRepository.findByIdAndSessionId(ATTEMPT, SESSION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.attempt(TEACHER, SESSION, ATTEMPT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ExaminationErrorCode.ATTEMPT_NOT_FOUND));
    }

    /** Học sinh trả lời đúng câu 1, bỏ trống câu 2, rồi nộp: 1/2 câu, 5 điểm. */
    private static Attempt submittedAttempt(Long studentId) {
        Attempt attempt = Attempt.start(SESSION, studentId, 30, SESSION_END, START);
        attempt.saveAnswer(1L, 0, START.plusSeconds(60));
        attempt.submit(GRADING, START.plusSeconds(120));
        return attempt;
    }

    /**
     * Kỳ thi đã mở với hai câu có ID 1 và 2. Dùng mock vì ID của câu chỉ có sau khi lưu vào database.
     * Stub {@code lenient} vì mỗi test dùng một phần khác nhau.
     */
    private static ExamSession openSession(ResultVisibility visibility) {
        ExamSession session = mock(ExamSession.class, withSettings().strictness(
                Strictness.LENIENT));
        when(session.getId()).thenReturn(SESSION);
        when(session.getTitle()).thenReturn("Kiểm tra 15 phút");
        when(session.getStatus()).thenReturn(SessionStatus.CLOSED);
        when(session.getResultVisibility()).thenReturn(visibility);
        when(session.getMaxScore()).thenReturn(10);
        List<SessionQuestion> questions = List.of(question(1L, 1, "Câu 1", 0), question(2L, 2, "Câu 2", 1));
        when(session.getQuestions()).thenReturn(questions);
        return session;
    }

    private static SessionQuestion question(Long id, int position, String content, int correctOption) {
        SessionQuestion question = mock(SessionQuestion.class, withSettings().strictness(
                Strictness.LENIENT));
        when(question.getId()).thenReturn(id);
        when(question.getPosition()).thenReturn(position);
        when(question.getContent()).thenReturn(content);
        when(question.getOptions()).thenReturn(List.of("A", "B"));
        when(question.getCorrectOption()).thenReturn(correctOption);
        return question;
    }
}
