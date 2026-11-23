package vn.exampractice.examination.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.exampractice.TestcontainersConfiguration;
import vn.exampractice.support.TestAuth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.exampractice.support.TestAuth.student;
import static vn.exampractice.support.TestAuth.teacher;

/**
 * Integration test cho phần làm bài (state-machines.md mục 2) trên PostgreSQL thật: bắt đầu, lưu đáp
 * án, nộp, chấm theo thang điểm, kết quả theo cài đặt xem kết quả, và giáo viên kết thúc sớm.
 *
 * <p>Mỗi kỳ thi dùng đề 3 câu, đáp án đúng lần lượt 0, 1, 0. Mỗi test chạy trong một transaction được
 * rollback khi kết thúc.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AttemptControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final AtomicLong NEXT_STUDENT = new AtomicLong(System.currentTimeMillis() + 5_000_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private final long student = NEXT_STUDENT.incrementAndGet();

    @Test
    void start_returns_201_with_questions_but_never_correct_answers() throws Exception {
        long session = openSession("SCORE_AND_ANSWERS", 10, student);

        var response = perform(post("/api/exam/sessions/" + session + "/attempts"), student(student), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.title").value("Kiểm tra"))
                .andExpect(jsonPath("$.totalQuestions").value(3))
                .andExpect(jsonPath("$.answeredCount").value(0))
                .andExpect(jsonPath("$.questions[0].position").value(1))
                .andExpect(jsonPath("$.questions[0].options[1]").value("B"))
                .andExpect(jsonPath("$.questions[0].correctOption", nullValue()))
                .andExpect(jsonPath("$.answersRevealed").value(false))
                .andExpect(jsonPath("$.score", nullValue()))
                .andReturn().getResponse();

        JsonNode attempt = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getHeader("Location")).endsWith("/api/exam/attempts/" + attempt.get("id").asLong());
        Duration length = Duration.between(Instant.parse(attempt.get("startedAt").asString()),
                Instant.parse(attempt.get("deadline").asString()));
        assertThat(length).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void start_requires_joining_first() throws Exception {
        long session = openSession("SCORE", 10, student);

        perform(post("/api/exam/sessions/" + session + "/attempts"), student(NEXT_STUDENT.incrementAndGet()), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_A_PARTICIPANT"));
    }

    @Test
    void start_requires_open_session() throws Exception {
        long session = scheduledSession("SCORE", 10);
        join(session, student);

        perform(post("/api/exam/sessions/" + session + "/attempts"), student(student), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_OPEN"));
    }

    @Test
    void only_one_attempt_per_session() throws Exception {
        long session = openSession("SCORE", 10, student);
        start(session);

        perform(post("/api/exam/sessions/" + session + "/attempts"), student(student), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_ALREADY_STARTED"));
    }

    /** Thoát ra rồi vào lại: GET trả đúng các đáp án đã lưu. */
    @Test
    void answers_are_saved_changed_and_shown_on_reload() throws Exception {
        JsonNode attempt = start(openSession("SCORE", 10, student));
        long id = attempt.get("id").asLong();
        long q1 = questionId(attempt, 0);
        long q2 = questionId(attempt, 1);

        answer(id, q1, 1).andExpect(status().isNoContent());
        answer(id, q1, 0).andExpect(status().isNoContent());
        answer(id, q2, 1).andExpect(status().isNoContent());

        perform(get("/api/exam/attempts/" + id), student(student), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answeredCount").value(2))
                .andExpect(jsonPath("$.questions[0].selectedOption").value(0))
                .andExpect(jsonPath("$.questions[1].selectedOption").value(1))
                .andExpect(jsonPath("$.questions[2].selectedOption", nullValue()));
    }

    @Test
    void invalid_option_or_foreign_question_is_rejected() throws Exception {
        JsonNode attempt = start(openSession("SCORE", 10, student));
        long id = attempt.get("id").asLong();

        answer(id, questionId(attempt, 0), 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        answer(id, 999_999L, 0)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_QUESTION_NOT_FOUND"));
        perform(put("/api/exam/attempts/" + id + "/answers/" + questionId(attempt, 0)), student(student),
                Map.of("selectedOption", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** Đúng 2/3 câu, thang 20: 2 × 20 / 3 = 13.33. */
    @Test
    void submit_grades_on_session_scale_and_shows_score() throws Exception {
        JsonNode attempt = start(openSession("SCORE", 20, student));
        long id = attempt.get("id").asLong();
        answerAll(attempt, 0, 1, 1);

        submit(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.correctCount").value(2))
                .andExpect(jsonPath("$.score").value(13.33))
                .andExpect(jsonPath("$.maxScore").value(20))
                .andExpect(jsonPath("$.answersRevealed").value(false))
                .andExpect(jsonPath("$.questions[0].correctOption", nullValue()));
    }

    @Test
    void hidden_visibility_hides_score_from_student() throws Exception {
        long session = openSession("HIDDEN", 10, student);
        JsonNode attempt = start(session);
        answerAll(attempt, 0, 1, 0);

        submit(attempt.get("id").asLong())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.score", nullValue()))
                .andExpect(jsonPath("$.correctCount", nullValue()));
        perform(get("/api/exam/my-sessions/" + session), student(student), null)
                .andExpect(jsonPath("$.attemptStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.score", nullValue()));
    }

    /** Đáp án đúng chỉ hiện khi kỳ thi đã đóng, để người nộp sớm không gửi đáp án cho người đang thi. */
    @Test
    void answers_are_revealed_only_after_the_session_closes() throws Exception {
        long session = openSession("SCORE_AND_ANSWERS", 10, student);
        JsonNode attempt = start(session);
        long id = attempt.get("id").asLong();
        answerAll(attempt, 0, 0, 0);
        submit(id)
                .andExpect(jsonPath("$.score").value(6.67))
                .andExpect(jsonPath("$.answersRevealed").value(false))
                .andExpect(jsonPath("$.questions[1].correctOption", nullValue()));

        teacherAction(session, "close");

        perform(get("/api/exam/attempts/" + id), student(student), null)
                .andExpect(jsonPath("$.answersRevealed").value(true))
                .andExpect(jsonPath("$.questions[1].correctOption").value(1))
                .andExpect(jsonPath("$.questions[1].correct").value(false))
                .andExpect(jsonPath("$.questions[0].correct").value(true));
    }

    @Test
    void nothing_can_be_written_after_submit() throws Exception {
        JsonNode attempt = start(openSession("SCORE", 10, student));
        long id = attempt.get("id").asLong();
        submit(id).andExpect(status().isOk());

        answer(id, questionId(attempt, 0), 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_ALREADY_SUBMITTED"));
        submit(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_ALREADY_SUBMITTED"));
    }

    /** ADR-0013: trễ dưới 5 giây vẫn lưu được, quá 5 giây thì hết giờ. */
    @Test
    void writes_after_deadline_plus_grace_are_rejected() throws Exception {
        JsonNode attempt = start(openSession("SCORE", 10, student));
        long id = attempt.get("id").asLong();

        moveDeadline(id, Duration.ofSeconds(-2));
        answer(id, questionId(attempt, 0), 0).andExpect(status().isNoContent());

        moveDeadline(id, Duration.ofSeconds(-10));
        answer(id, questionId(attempt, 1), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));
        submit(id).andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));
    }

    @Test
    void attempt_of_another_student_is_not_found() throws Exception {
        JsonNode attempt = start(openSession("SCORE", 10, student));
        long id = attempt.get("id").asLong();
        long other = NEXT_STUDENT.incrementAndGet();

        perform(get("/api/exam/attempts/" + id), student(other), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTEMPT_NOT_FOUND"));
        perform(put("/api/exam/attempts/" + id + "/answers/" + questionId(attempt, 0)), student(other),
                Map.of("selectedOption", 0))
                .andExpect(status().isNotFound());
        perform(post("/api/exam/attempts/" + id + "/submit"), student(other), null)
                .andExpect(status().isNotFound());
    }

    /** state-machines.md mục 1: kỳ thi đóng thì bài đang làm chuyển EXPIRED và được chấm. */
    @Test
    void closing_the_session_expires_and_grades_attempts_in_progress() throws Exception {
        long session = openSession("SCORE", 10, student);
        JsonNode attempt = start(session);
        long id = attempt.get("id").asLong();
        answer(id, questionId(attempt, 0), 0).andExpect(status().isNoContent());

        teacherAction(session, "close");

        perform(get("/api/exam/attempts/" + id), student(student), null)
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.score").value(3.33))
                .andExpect(jsonPath("$.submittedAt").exists());
    }

    @Test
    void my_sessions_show_the_attempt_and_its_score() throws Exception {
        long session = openSession("SCORE", 10, student);
        JsonNode attempt = start(session);
        long id = attempt.get("id").asLong();

        perform(get("/api/exam/my-sessions"), student(student), null)
                .andExpect(jsonPath("$.content[0].attemptId").value(id))
                .andExpect(jsonPath("$.content[0].attemptStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.content[0].score", nullValue()));

        answerAll(attempt, 0, 1, 0);
        submit(id).andExpect(status().isOk());

        perform(get("/api/exam/my-sessions/" + session), student(student), null)
                .andExpect(jsonPath("$.attemptStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.correctCount").value(3))
                .andExpect(jsonPath("$.score").value(10.0));
    }

    @Test
    void teachers_cannot_take_exams() throws Exception {
        perform(post("/api/exam/sessions/1/attempts"), teacher(TEACHER_ID), null)
                .andExpect(status().isForbidden());
        perform(get("/api/exam/attempts/1"), teacher(TEACHER_ID), null)
                .andExpect(status().isForbidden());
    }

    // ---------- tiện ích ----------

    private JsonNode start(long sessionId) throws Exception {
        String json = perform(post("/api/exam/sessions/" + sessionId + "/attempts"), student(student), null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private ResultActions answer(long attemptId, long questionId, int option) throws Exception {
        return perform(put("/api/exam/attempts/" + attemptId + "/answers/" + questionId), student(student),
                Map.of("selectedOption", option));
    }

    private void answerAll(JsonNode attempt, int... options) throws Exception {
        for (int i = 0; i < options.length; i++) {
            answer(attempt.get("id").asLong(), questionId(attempt, i), options[i]).andExpect(status().isNoContent());
        }
    }

    private ResultActions submit(long attemptId) throws Exception {
        return perform(post("/api/exam/attempts/" + attemptId + "/submit"), student(student), null);
    }

    private static long questionId(JsonNode attempt, int index) {
        return attempt.get("questions").get(index).get("questionId").asLong();
    }

    /**
     * Dời hạn chót về quá khứ, rồi xóa bộ nhớ đệm của Hibernate: request sau trong cùng transaction
     * phải đọc lại hạn chót mới từ database.
     */
    private void moveDeadline(long attemptId, Duration fromNow) {
        entityManager.flush();
        jdbc.update("UPDATE exam_attempt SET started_at = now() - interval '20 minutes', deadline = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().plus(fromNow)), attemptId);
        entityManager.clear();
    }

    /** Kỳ thi đã mở, học sinh đã tham gia; đề 3 câu, đáp án đúng 0, 1, 0, thời lượng 15 phút. */
    private long openSession(String visibility, int maxScore, long studentId) throws Exception {
        long session = scheduledSession(visibility, maxScore);
        join(session, studentId);
        teacherAction(session, "open");
        return session;
    }

    private long scheduledSession(String visibility, int maxScore) throws Exception {
        List<Long> questions = List.of(question(0), question(1), question(0));
        long exam = idOf(perform(post("/api/design/exams"), teacher(TEACHER_ID),
                Map.of("title", "Kiểm tra", "durationMinutes", 15)));
        perform(put("/api/design/exams/" + exam + "/questions"), teacher(TEACHER_ID),
                Map.of("questionIds", questions)).andExpect(status().isOk());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("examId", exam);
        body.put("startAt", Instant.now().plus(Duration.ofHours(1)).toString());
        body.put("endAt", Instant.now().plus(Duration.ofHours(3)).toString());
        body.put("resultVisibility", visibility);
        body.put("maxScore", maxScore);
        return idOf(perform(post("/api/exam/sessions"), teacher(TEACHER_ID), body));
    }

    private long question(int correctOption) throws Exception {
        return idOf(perform(post("/api/design/questions"), teacher(TEACHER_ID),
                Map.of("content", "Câu hỏi", "options", List.of("A", "B"), "correctOption", correctOption)));
    }

    private void join(long sessionId, long studentId) throws Exception {
        String code = objectMapper.readTree(perform(get("/api/exam/sessions/" + sessionId), teacher(TEACHER_ID), null)
                .andReturn().getResponse().getContentAsString()).get("accessCode").asString();
        perform(post("/api/exam/sessions/join"), student(studentId), Map.of("accessCode", code))
                .andExpect(status().isOk());
    }

    private void teacherAction(long sessionId, String action) throws Exception {
        perform(post("/api/exam/sessions/" + sessionId + "/" + action), teacher(TEACHER_ID), null)
                .andExpect(status().isOk());
    }

    private long idOf(ResultActions created) throws Exception {
        String json = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, RequestPostProcessor user, Object body)
            throws Exception {
        request.with(user);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }
}
