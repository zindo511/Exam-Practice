package vn.exampractice.examination.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.exampractice.support.TestAuth.student;
import static vn.exampractice.support.TestAuth.teacher;

/**
 * Integration test cho luồng của học sinh: tham gia bằng mã ({@code /api/exam/participations}), làm
 * bài và nộp ({@code /api/exam/attempts}), cùng kết quả phía giáo viên và việc đóng kỳ thi khi còn
 * bài đang làm. Chạy trên PostgreSQL thật; đề, kỳ thi được dựng qua API như giáo viên làm thật.
 *
 * <p>Mỗi test chạy trong một transaction được rollback khi kết thúc. Khi cần giả lập hết giờ, test
 * sửa {@code deadline} bằng SQL rồi xóa bộ nhớ đệm của EntityManager để request sau đọc lại.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AttemptControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long OTHER_TEACHER_ID = 2L;
    private static final long STUDENT_ID = 10L;
    private static final long OTHER_STUDENT_ID = 11L;
    private static final String JOIN_URL = "/api/exam/participations";
    private static final String ATTEMPTS_URL = "/api/exam/attempts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private long sessionId;
    private String accessCode;

    /** Kỳ thi đang mở với ba câu; đáp án đúng lần lượt là 0, 1, 2. */
    @BeforeEach
    void openSession() throws Exception {
        long exam = createExam(TEACHER_ID);
        List<Long> questions = List.of(
                createQuestion(TEACHER_ID, "Câu 1", 0),
                createQuestion(TEACHER_ID, "Câu 2", 1),
                createQuestion(TEACHER_ID, "Câu 3", 2));
        perform(put("/api/design/exams/" + exam + "/questions"), teacher(TEACHER_ID),
                Map.of("questionIds", questions)).andExpect(status().isOk());
        JsonNode session = json(perform(post("/api/exam/sessions"), teacher(TEACHER_ID),
                sessionBody(exam, hoursFromNow(1), hoursFromNow(3)))
                .andExpect(status().isCreated()));
        sessionId = session.get("id").asLong();
        accessCode = session.get("accessCode").asString();
        perform(post("/api/exam/sessions/" + sessionId + "/open"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk());
    }

    // ---------- tham gia ----------

    @Test
    void join_by_code_is_case_insensitive_and_idempotent() throws Exception {
        perform(post(JOIN_URL), student(STUDENT_ID), Map.of("accessCode", "  " + accessCode.toLowerCase() + " "))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith(JOIN_URL + "/" + sessionId)))
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.sessionStatus").value("OPEN"))
                .andExpect(jsonPath("$.durationMinutes").value(15))
                .andExpect(jsonPath("$.attemptId").value(nullValue()))
                .andExpect(jsonPath("$.accessCode").doesNotExist());

        perform(post(JOIN_URL), student(STUDENT_ID), Map.of("accessCode", accessCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_participant WHERE session_id = ?",
                Integer.class, sessionId)).isEqualTo(1);
    }

    @Test
    void wrong_or_inactive_code_is_rejected() throws Exception {
        expectCode(perform(post(JOIN_URL), student(STUDENT_ID), Map.of("accessCode", "ZZZZZZ")),
                409, "INVALID_ACCESS_CODE");

        perform(post("/api/exam/sessions/" + sessionId + "/lock-join"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk());
        expectCode(perform(post(JOIN_URL), student(STUDENT_ID), Map.of("accessCode", accessCode)),
                409, "INVALID_ACCESS_CODE");

        perform(post("/api/exam/sessions/" + sessionId + "/unlock-join"), teacher(TEACHER_ID), null);
        perform(post("/api/exam/sessions/" + sessionId + "/close"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk());
        expectCode(perform(post(JOIN_URL), student(STUDENT_ID), Map.of("accessCode", accessCode)),
                409, "INVALID_ACCESS_CODE");
    }

    /** Người đã vào phòng chờ nhập lại mã sau khi giáo viên khóa tham gia vẫn vào được. */
    @Test
    void existing_participant_can_rejoin_after_join_is_locked() throws Exception {
        join(STUDENT_ID);
        perform(post("/api/exam/sessions/" + sessionId + "/lock-join"), teacher(TEACHER_ID), null);

        perform(post(JOIN_URL), student(STUDENT_ID), Map.of("accessCode", accessCode))
                .andExpect(status().isOk());
    }

    @Test
    void join_with_blank_code_is_validation_error() throws Exception {
        expectCode(perform(post(JOIN_URL), student(STUDENT_ID), Map.of("accessCode", " ")),
                400, "VALIDATION_FAILED");
    }

    @Test
    void waiting_room_shows_attempt_once_started_and_is_hidden_from_others() throws Exception {
        join(STUDENT_ID);
        long attempt = start(STUDENT_ID);

        perform(get(JOIN_URL + "/" + sessionId), student(STUDENT_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attempt))
                .andExpect(jsonPath("$.attemptStatus").value("IN_PROGRESS"));
        expectCode(perform(get(JOIN_URL + "/" + sessionId), student(OTHER_STUDENT_ID), null),
                404, "SESSION_NOT_FOUND");
    }

    // ---------- bắt đầu làm bài ----------

    @Test
    void start_returns_questions_without_correct_answers() throws Exception {
        join(STUDENT_ID);

        perform(post(ATTEMPTS_URL), student(STUDENT_ID), Map.of("sessionId", sessionId))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.deadline", not(nullValue())))
                .andExpect(jsonPath("$.serverTime", not(nullValue())))
                .andExpect(jsonPath("$.totalQuestions").value(3))
                .andExpect(jsonPath("$.answeredCount").value(0))
                .andExpect(jsonPath("$.score").value(nullValue()))
                .andExpect(jsonPath("$.questions[0].position").value(1))
                .andExpect(jsonPath("$.questions[0].content").value("Câu 1"))
                .andExpect(jsonPath("$.questions[0].options.length()").value(3))
                .andExpect(jsonPath("$.questions[0].correctOption").doesNotExist());
    }

    @Test
    void start_checks_participation_session_state_and_single_attempt() throws Exception {
        expectCode(perform(post(ATTEMPTS_URL), student(STUDENT_ID), Map.of("sessionId", sessionId)),
                409, "NOT_A_PARTICIPANT");
        expectCode(perform(post(ATTEMPTS_URL), student(STUDENT_ID), Map.of("sessionId", 999_999L)),
                409, "NOT_A_PARTICIPANT");

        join(STUDENT_ID);
        start(STUDENT_ID);
        expectCode(perform(post(ATTEMPTS_URL), student(STUDENT_ID), Map.of("sessionId", sessionId)),
                409, "ATTEMPT_ALREADY_STARTED");
    }

    @Test
    void cannot_start_before_session_opens() throws Exception {
        long scheduled = json(perform(post("/api/exam/sessions"), teacher(TEACHER_ID),
                sessionBody(usableExam(), hoursFromNow(1), hoursFromNow(3))).andExpect(status().isCreated()))
                .get("id").asLong();
        String code = jdbc.queryForObject("SELECT access_code FROM exam_session WHERE id = ?", String.class, scheduled);
        perform(post(JOIN_URL), student(STUDENT_ID), Map.of("accessCode", code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionStatus").value("SCHEDULED"));

        expectCode(perform(post(ATTEMPTS_URL), student(STUDENT_ID), Map.of("sessionId", scheduled)),
                409, "SESSION_NOT_OPEN");
    }

    @Test
    void teachers_cannot_use_student_endpoints() throws Exception {
        perform(post(JOIN_URL), teacher(TEACHER_ID), Map.of("accessCode", accessCode))
                .andExpect(status().isForbidden());
        perform(post(ATTEMPTS_URL), teacher(TEACHER_ID), Map.of("sessionId", sessionId))
                .andExpect(status().isForbidden());
    }

    // ---------- làm bài và nộp ----------

    @Test
    void autosave_change_clear_and_resume_after_reload() throws Exception {
        long attempt = joinAndStart(STUDENT_ID);

        perform(put(answerUrl(attempt, 1)), student(STUDENT_ID), Map.of("selectedOption", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.selectedOption").value(2))
                .andExpect(jsonPath("$.deadline", not(nullValue())));
        saveAnswer(attempt, 1, 0);
        saveAnswer(attempt, 2, 1);
        saveAnswer(attempt, 3, 0);
        perform(delete(answerUrl(attempt, 3)), student(STUDENT_ID), null).andExpect(status().isNoContent());

        perform(get(ATTEMPTS_URL + "/" + attempt), student(STUDENT_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answeredCount").value(2))
                .andExpect(jsonPath("$.questions[0].selectedOption").value(0))
                .andExpect(jsonPath("$.questions[1].selectedOption").value(1))
                .andExpect(jsonPath("$.questions[2].selectedOption").value(nullValue()));
    }

    @Test
    void invalid_question_or_option_is_rejected() throws Exception {
        long attempt = joinAndStart(STUDENT_ID);

        expectCode(perform(put(answerUrl(attempt, 4)), student(STUDENT_ID), Map.of("selectedOption", 0)),
                404, "NOT_FOUND");
        expectCode(perform(put(answerUrl(attempt, 1)), student(STUDENT_ID), Map.of("selectedOption", 3)),
                400, "VALIDATION_FAILED");
        expectCode(perform(put(answerUrl(attempt, 1)), student(STUDENT_ID), Map.of("selectedOption", -1)),
                400, "VALIDATION_FAILED");
    }

    @Test
    void submit_grades_on_ten_point_scale_and_locks_answers() throws Exception {
        long attempt = joinAndStart(STUDENT_ID);
        saveAnswer(attempt, 1, 0);
        saveAnswer(attempt, 2, 1);
        saveAnswer(attempt, 3, 0);

        perform(post(ATTEMPTS_URL + "/" + attempt + "/submit"), student(STUDENT_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt", not(nullValue())))
                .andExpect(jsonPath("$.correctCount").value(2))
                .andExpect(jsonPath("$.score").value(6.67));

        expectCode(perform(put(answerUrl(attempt, 3)), student(STUDENT_ID), Map.of("selectedOption", 2)),
                409, "ATTEMPT_ALREADY_SUBMITTED");
        expectCode(perform(post(ATTEMPTS_URL + "/" + attempt + "/submit"), student(STUDENT_ID), null),
                409, "ATTEMPT_ALREADY_SUBMITTED");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, correct_count, score FROM exam_attempt WHERE id = ?", attempt);
        assertThat(row.get("status")).isEqualTo("SUBMITTED");
        assertThat(((Number) row.get("correct_count")).intValue()).isEqualTo(2);
        assertThat(row.get("score").toString()).isEqualTo("6.67");
    }

    @Test
    void writes_after_deadline_plus_grace_are_rejected() throws Exception {
        long attempt = joinAndStart(STUDENT_ID);
        moveDeadline(attempt, Instant.now().minusSeconds(6));

        expectCode(perform(put(answerUrl(attempt, 1)), student(STUDENT_ID), Map.of("selectedOption", 0)),
                409, "ATTEMPT_EXPIRED");
        expectCode(perform(post(ATTEMPTS_URL + "/" + attempt + "/submit"), student(STUDENT_ID), null),
                409, "ATTEMPT_EXPIRED");
    }

    @Test
    void submit_within_grace_is_accepted() throws Exception {
        long attempt = joinAndStart(STUDENT_ID);
        moveDeadline(attempt, Instant.now().minusSeconds(2));

        perform(post(ATTEMPTS_URL + "/" + attempt + "/submit"), student(STUDENT_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void attempt_of_another_student_is_not_found() throws Exception {
        long attempt = joinAndStart(STUDENT_ID);

        for (MockHttpServletRequestBuilder request : List.of(get(ATTEMPTS_URL + "/" + attempt),
                post(ATTEMPTS_URL + "/" + attempt + "/submit"), delete(answerUrl(attempt, 1)))) {
            expectCode(perform(request, student(OTHER_STUDENT_ID), null), 404, "ATTEMPT_NOT_FOUND");
        }
        expectCode(perform(put(answerUrl(attempt, 1)), student(OTHER_STUDENT_ID), Map.of("selectedOption", 0)),
                404, "ATTEMPT_NOT_FOUND");
    }

    // ---------- giáo viên ----------

    @Test
    void closing_session_expires_and_grades_attempts_in_progress() throws Exception {
        long inProgress = joinAndStart(STUDENT_ID);
        saveAnswer(inProgress, 1, 0);
        long submitted = joinAndStart(OTHER_STUDENT_ID);
        perform(post(ATTEMPTS_URL + "/" + submitted + "/submit"), student(OTHER_STUDENT_ID), null)
                .andExpect(status().isOk());

        perform(post("/api/exam/sessions/" + sessionId + "/close"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk());

        perform(get(ATTEMPTS_URL + "/" + inProgress), student(STUDENT_ID), null)
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.score").value(3.33));
        perform(get(ATTEMPTS_URL + "/" + submitted), student(OTHER_STUDENT_ID), null)
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        expectCode(perform(put(answerUrl(inProgress, 2)), student(STUDENT_ID), Map.of("selectedOption", 1)),
                409, "ATTEMPT_EXPIRED");
    }

    @Test
    void teacher_sees_results_of_own_session_only() throws Exception {
        long first = joinAndStart(STUDENT_ID);
        saveAnswer(first, 1, 0);
        perform(post(ATTEMPTS_URL + "/" + first + "/submit"), student(STUDENT_ID), null);
        joinAndStart(OTHER_STUDENT_ID);

        perform(get("/api/exam/sessions/" + sessionId + "/attempts"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].studentId").value(STUDENT_ID))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].answeredCount").value(1))
                .andExpect(jsonPath("$[0].totalQuestions").value(3))
                .andExpect(jsonPath("$[0].score").value(3.33))
                .andExpect(jsonPath("$[1].studentId").value(OTHER_STUDENT_ID))
                .andExpect(jsonPath("$[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].score").value(nullValue()));

        expectCode(perform(get("/api/exam/sessions/" + sessionId + "/attempts"), teacher(OTHER_TEACHER_ID), null),
                404, "SESSION_NOT_FOUND");
        perform(get("/api/exam/sessions/" + sessionId + "/attempts"), student(STUDENT_ID), null)
                .andExpect(status().isForbidden());
    }

    // ---------- tiện ích ----------

    private void join(long studentId) throws Exception {
        perform(post(JOIN_URL), student(studentId), Map.of("accessCode", accessCode))
                .andExpect(status().isCreated());
    }

    private long start(long studentId) throws Exception {
        return json(perform(post(ATTEMPTS_URL), student(studentId), Map.of("sessionId", sessionId))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long joinAndStart(long studentId) throws Exception {
        join(studentId);
        return start(studentId);
    }

    private void saveAnswer(long attempt, int position, int option) throws Exception {
        perform(put(answerUrl(attempt, position)), ownerOf(attempt), Map.of("selectedOption", option))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor ownerOf(long attempt) {
        Long owner = jdbc.queryForObject("SELECT student_id FROM exam_attempt WHERE id = ?", Long.class, attempt);
        return student(owner);
    }

    /** Ghi hạn chót mới bằng SQL rồi bỏ bộ nhớ đệm để request sau đọc lại từ database. */
    private void moveDeadline(long attempt, Instant deadline) {
        entityManager.flush();
        jdbc.update("UPDATE exam_attempt SET deadline = ?, started_at = ? WHERE id = ?",
                java.sql.Timestamp.from(deadline), java.sql.Timestamp.from(deadline.minus(Duration.ofMinutes(15))),
                attempt);
        entityManager.clear();
    }

    private static String answerUrl(long attempt, int position) {
        return ATTEMPTS_URL + "/" + attempt + "/answers/" + position;
    }

    private long usableExam() throws Exception {
        long exam = createExam(TEACHER_ID);
        long question = createQuestion(TEACHER_ID, "Câu", 0);
        perform(put("/api/design/exams/" + exam + "/questions"), teacher(TEACHER_ID),
                Map.of("questionIds", List.of(question))).andExpect(status().isOk());
        return exam;
    }

    private long createExam(long teacherId) throws Exception {
        return json(perform(post("/api/design/exams"), teacher(teacherId),
                Map.of("title", "Đề", "durationMinutes", 15)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long createQuestion(long teacherId, String content, int correctOption) throws Exception {
        return json(perform(post("/api/design/questions"), teacher(teacherId),
                Map.of("content", content, "options", List.of("A", "B", "C"), "correctOption", correctOption))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, RequestPostProcessor user, Object body)
            throws Exception {
        request.with(user);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static void expectCode(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private static Map<String, Object> sessionBody(long examId, Instant startAt, Instant endAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("examId", examId);
        body.put("startAt", startAt.toString());
        body.put("endAt", endAt.toString());
        return body;
    }

    private static Instant hoursFromNow(int hours) {
        return Instant.now().plus(Duration.ofHours(hours));
    }
}
