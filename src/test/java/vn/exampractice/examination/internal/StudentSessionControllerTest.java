package vn.exampractice.examination.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.exampractice.support.TestAuth.student;
import static vn.exampractice.support.TestAuth.teacher;

/**
 * Integration test cho phía học sinh của kỳ thi: nhập mã, giới hạn nhập sai, phòng chờ (ADR-0010).
 *
 * <p>Mỗi test chạy trong một transaction được rollback, <b>trừ</b> các lần nhập sai: chúng được ghi
 * trong transaction riêng nên còn lại sau test. Vì vậy mỗi test dùng một {@code studentId} mới để
 * lần sai của test này không ảnh hưởng test khác. Việc lần sai thực sự được lưu dù request bị
 * rollback được kiểm tra ở {@link JoinFailureLimitTest}, với transaction thật.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class StudentSessionControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final AtomicLong NEXT_STUDENT = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void join_with_code_adds_student_and_hides_teacher_only_fields() throws Exception {
        long student = newStudent();
        JsonNode session = scheduledSession();

        join(student, session.get("accessCode").asString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.get("id").asLong()))
                .andExpect(jsonPath("$.title").value("Kiểm tra"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.resultVisibility").value("SCORE"))
                .andExpect(jsonPath("$.maxScore").value(20))
                .andExpect(jsonPath("$.accessCode").doesNotExist())
                .andExpect(jsonPath("$.examId").doesNotExist());

        assertThat(participants(session.get("id").asLong(), student)).isEqualTo(1);
    }

    @Test
    void code_is_trimmed_and_case_insensitive() throws Exception {
        JsonNode session = scheduledSession();

        join(newStudent(), "  " + session.get("accessCode").asString().toLowerCase() + " ")
                .andExpect(status().isOk());
    }

    @Test
    void joining_again_keeps_one_row_even_after_join_is_locked() throws Exception {
        long student = newStudent();
        JsonNode session = scheduledSession();
        long sessionId = session.get("id").asLong();
        String code = session.get("accessCode").asString();
        join(student, code).andExpect(status().isOk());
        teacherAction(sessionId, "lock-join");

        join(student, code).andExpect(status().isOk());
        join(newStudent(), code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));

        assertThat(participants(sessionId, student)).isEqualTo(1);
    }

    /** Mã đúng của kỳ thi đã khóa: bị từ chối nhưng không tính lần sai, nên bấm thử nhiều lần không bị chặn. */
    @Test
    void right_code_of_locked_session_is_rejected_without_counting_as_failure() throws Exception {
        long student = newStudent();
        JsonNode session = scheduledSession();
        teacherAction(session.get("id").asLong(), "lock-join");

        for (int i = 0; i <= JoinAttemptLimiter.MAX_FAILURES; i++) {
            join(student, session.get("accessCode").asString())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));
        }

        assertThat(failures(student)).isZero();
    }

    @Test
    void ended_session_does_not_accept_new_students() throws Exception {
        JsonNode session = scheduledSession();
        teacherAction(session.get("id").asLong(), "cancel");

        join(newStudent(), session.get("accessCode").asString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));
    }

    /** Nhập sai trả lỗi chung; việc lần sai được lưu thật nằm ở JoinFailureLimitTest (transaction thật). */
    @Test
    void wrong_code_is_rejected() throws Exception {
        join(newStudent(), "ZZZZZZ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));
    }

    @Test
    void five_recent_failures_block_even_the_right_code() throws Exception {
        long student = newStudent();
        Instant recent = Instant.now().minus(Duration.ofMinutes(5));
        for (int i = 0; i < JoinAttemptLimiter.MAX_FAILURES; i++) {
            jdbc.update("INSERT INTO exam_join_failure (student_id, failed_at) VALUES (?, ?)",
                    student, java.sql.Timestamp.from(recent.plusSeconds(i)));
        }

        join(student, scheduledSession().get("accessCode").asString())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCESS_CODE_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void old_failures_no_longer_block() throws Exception {
        long student = newStudent();
        Instant longAgo = Instant.now().minus(Duration.ofMinutes(40));
        for (int i = 0; i < JoinAttemptLimiter.MAX_FAILURES; i++) {
            jdbc.update("INSERT INTO exam_join_failure (student_id, failed_at) VALUES (?, ?)",
                    student, java.sql.Timestamp.from(longAgo.plusSeconds(i)));
        }

        join(student, scheduledSession().get("accessCode").asString()).andExpect(status().isOk());
    }

    @Test
    void my_sessions_lists_joined_sessions_latest_start_first() throws Exception {
        long student = newStudent();
        JsonNode early = scheduledSession(Instant.now().plus(Duration.ofHours(1)));
        JsonNode late = scheduledSession(Instant.now().plus(Duration.ofHours(5)));
        scheduledSession();
        join(student, early.get("accessCode").asString()).andExpect(status().isOk());
        join(student, late.get("accessCode").asString()).andExpect(status().isOk());

        perform(get("/api/exam/my-sessions"), student(student), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(late.get("id").asLong()))
                .andExpect(jsonPath("$.content[1].id").value(early.get("id").asLong()));
    }

    /** Quay lại phòng chờ không cần mã; thấy được khi kỳ thi chuyển sang OPEN. */
    @Test
    void waiting_room_shows_current_state_without_code() throws Exception {
        long student = newStudent();
        JsonNode session = scheduledSession();
        long sessionId = session.get("id").asLong();
        join(student, session.get("accessCode").asString()).andExpect(status().isOk());

        teacherAction(sessionId, "open");

        perform(get("/api/exam/my-sessions/" + sessionId), student(student), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.durationMinutes").value(15));
    }

    @Test
    void waiting_room_of_a_session_not_joined_is_not_found() throws Exception {
        long sessionId = scheduledSession().get("id").asLong();

        perform(get("/api/exam/my-sessions/" + sessionId), student(newStudent()), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void blank_code_is_invalid() throws Exception {
        perform(post("/api/exam/sessions/join"), student(newStudent()), Map.of("accessCode", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("accessCode"));
    }

    @Test
    void teachers_cannot_join() throws Exception {
        perform(post("/api/exam/sessions/join"), teacher(TEACHER_ID), Map.of("accessCode", "ABC123"))
                .andExpect(status().isForbidden());
        perform(get("/api/exam/my-sessions"), teacher(TEACHER_ID), null)
                .andExpect(status().isForbidden());
    }

    // ---------- tiện ích ----------

    private static long newStudent() {
        return NEXT_STUDENT.incrementAndGet();
    }

    private ResultActions join(long studentId, String code) throws Exception {
        return perform(post("/api/exam/sessions/join"), student(studentId), Map.of("accessCode", code));
    }

    /** Kỳ thi SCHEDULED từ một đề có 1 câu, dựng qua API của giáo viên. */
    private JsonNode scheduledSession() throws Exception {
        return scheduledSession(Instant.now().plus(Duration.ofHours(1)));
    }

    private JsonNode scheduledSession(Instant startAt) throws Exception {
        long question = idOf(perform(post("/api/design/questions"), teacher(TEACHER_ID),
                Map.of("content", "Câu hỏi", "options", List.of("A", "B"), "correctOption", 0)));
        long exam = idOf(perform(post("/api/design/exams"), teacher(TEACHER_ID),
                Map.of("title", "Kiểm tra", "durationMinutes", 15)));
        perform(put("/api/design/exams/" + exam + "/questions"), teacher(TEACHER_ID),
                Map.of("questionIds", List.of(question))).andExpect(status().isOk());
        String json = perform(post("/api/exam/sessions"), teacher(TEACHER_ID), Map.of("examId", exam,
                "startAt", startAt.toString(), "endAt", startAt.plus(Duration.ofHours(2)).toString(),
                "resultVisibility", "SCORE", "maxScore", 20))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private void teacherAction(long sessionId, String action) throws Exception {
        perform(post("/api/exam/sessions/" + sessionId + "/" + action), teacher(TEACHER_ID), null)
                .andExpect(status().isOk());
    }

    private int participants(long sessionId, long studentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM exam_participant WHERE session_id = ? AND student_id = ?",
                Integer.class, sessionId, studentId);
    }

    private int failures(long studentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM exam_join_failure WHERE student_id = ?",
                Integer.class, studentId);
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
