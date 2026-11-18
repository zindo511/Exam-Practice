package vn.exampractice.examination.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.exampractice.support.TestAuth.student;
import static vn.exampractice.support.TestAuth.teacher;

/**
 * Integration test cho {@code /api/exam/sessions} trên PostgreSQL thật, gồm cả lời gọi sang
 * examdesign qua {@code ExamDesignApi}. Đề và câu hỏi được tạo bằng API của examdesign, như giáo
 * viên làm thật, vì examination không được chạm vào class nội bộ của examdesign.
 *
 * <p>Mỗi test chạy trong một transaction được rollback khi kết thúc.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ExamSessionControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long OTHER_TEACHER_ID = 2L;
    private static final String URL = "/api/exam/sessions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void create_returns_201_with_code_and_title_copied_from_exam() throws Exception {
        long exam = usableExam(TEACHER_ID, "Kiểm tra giữa kỳ");

        var response = perform(post(URL), teacher(TEACHER_ID), sessionBody(exam, hoursFromNow(1), hoursFromNow(3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.examId").value(exam))
                .andExpect(jsonPath("$.title").value("Kiểm tra giữa kỳ"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.joinOpen").value(true))
                .andExpect(jsonPath("$.accessCode", matchesPattern("^[A-Z0-9]{6}$")))
                .andExpect(jsonPath("$.durationMinutes", nullValue()))
                .andReturn().getResponse();

        long id = json(response.getContentAsString()).get("id").asLong();
        assertThat(response.getHeader("Location")).endsWith(URL + "/" + id);
    }

    @Test
    void create_with_invalid_times_returns_errors_per_field() throws Exception {
        long exam = usableExam(TEACHER_ID, "Đề");

        perform(post(URL), teacher(TEACHER_ID), sessionBody(exam, hoursFromNow(3), hoursFromNow(-1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("endAt")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("startBeforeEnd")));
    }

    @Test
    void create_from_unusable_exam_is_rejected() throws Exception {
        long empty = createExam(TEACHER_ID, "Đề rỗng");
        long foreign = usableExam(OTHER_TEACHER_ID, "Đề của người khác");

        for (long exam : List.of(empty, foreign, 999_999L)) {
            perform(post(URL), teacher(TEACHER_ID), sessionBody(exam, hoursFromNow(1), hoursFromNow(3)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EXAM_NOT_AVAILABLE"));
        }
    }

    @Test
    void open_takes_snapshot_and_later_edits_do_not_change_it() throws Exception {
        long question = createQuestion(TEACHER_ID, "Câu gốc");
        long exam = createExam(TEACHER_ID, "Đề");
        replaceQuestions(exam, question);
        long session = createSession(exam);

        perform(post(URL + "/" + session + "/open"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.durationMinutes").value(15))
                .andExpect(jsonPath("$.openedAt", not(nullValue())));

        perform(put("/api/design/questions/" + question), teacher(TEACHER_ID),
                Map.of("content", "Câu đã sửa", "options", List.of("A", "B"), "correctOption", 1))
                .andExpect(status().isOk());

        Map<String, Object> copy = jdbc.queryForMap(
                "SELECT content, correct_option FROM exam_session_question WHERE session_id = ?", session);
        assertThat(copy.get("content")).isEqualTo("Câu gốc");
        assertThat(((Number) copy.get("correct_option")).intValue()).isZero();
    }

    /** state-machines.md mục 3: đề bị lưu trữ sau khi lên lịch thì kỳ thi không mở được. */
    @Test
    void session_cannot_open_when_exam_was_archived_after_scheduling() throws Exception {
        long exam = usableExam(TEACHER_ID, "Đề");
        long session = createSession(exam);
        perform(delete("/api/design/exams/" + exam), teacher(TEACHER_ID), null).andExpect(status().isNoContent());

        perform(post(URL + "/" + session + "/open"), teacher(TEACHER_ID), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_AVAILABLE"));
        perform(get(URL + "/" + session), teacher(TEACHER_ID), null)
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void wrong_state_transitions_are_conflicts() throws Exception {
        long session = createSession(usableExam(TEACHER_ID, "Đề"));

        expectInvalidState(perform(post(URL + "/" + session + "/close"), teacher(TEACHER_ID), null));
        perform(post(URL + "/" + session + "/open"), teacher(TEACHER_ID), null).andExpect(status().isOk());
        expectInvalidState(perform(post(URL + "/" + session + "/open"), teacher(TEACHER_ID), null));
        expectInvalidState(perform(post(URL + "/" + session + "/cancel"), teacher(TEACHER_ID), null));
    }

    @Test
    void close_ends_open_session() throws Exception {
        long session = createSession(usableExam(TEACHER_ID, "Đề"));
        perform(post(URL + "/" + session + "/open"), teacher(TEACHER_ID), null).andExpect(status().isOk());

        perform(post(URL + "/" + session + "/close"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.joinOpen").value(false))
                .andExpect(jsonPath("$.closedAt", not(nullValue())));
        expectInvalidState(perform(post(URL + "/" + session + "/lock-join"), teacher(TEACHER_ID), null));
    }

    @Test
    void cancel_scheduled_session() throws Exception {
        long session = createSession(usableExam(TEACHER_ID, "Đề"));

        perform(post(URL + "/" + session + "/cancel"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.joinOpen").value(false));
    }

    @Test
    void lock_and_unlock_join() throws Exception {
        long session = createSession(usableExam(TEACHER_ID, "Đề"));

        perform(post(URL + "/" + session + "/lock-join"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinOpen").value(false));
        perform(post(URL + "/" + session + "/unlock-join"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinOpen").value(true));
    }

    @Test
    void list_shows_own_sessions_latest_start_first_and_filters_by_status() throws Exception {
        long exam = usableExam(TEACHER_ID, "Đề");
        long early = createSession(exam, hoursFromNow(1), hoursFromNow(3));
        long late = createSession(exam, hoursFromNow(5), hoursFromNow(7));
        perform(post(URL + "/" + early + "/cancel"), teacher(TEACHER_ID), null).andExpect(status().isOk());
        createSessionAs(OTHER_TEACHER_ID, usableExam(OTHER_TEACHER_ID, "Đề khác"));

        perform(get(URL), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(late))
                .andExpect(jsonPath("$.content[1].id").value(early));

        perform(get(URL).param("status", "CANCELLED"), teacher(TEACHER_ID), null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(early));
    }

    @Test
    void session_of_another_teacher_is_not_found() throws Exception {
        long session = createSessionAs(OTHER_TEACHER_ID, usableExam(OTHER_TEACHER_ID, "Đề khác"));

        for (MockHttpServletRequestBuilder request : List.of(get(URL + "/" + session),
                post(URL + "/" + session + "/open"), post(URL + "/" + session + "/cancel"),
                post(URL + "/" + session + "/lock-join"))) {
            perform(request, teacher(TEACHER_ID), null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        }
    }

    @Test
    void students_cannot_manage_sessions() throws Exception {
        perform(get(URL), student(3L), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        perform(post(URL), student(3L), sessionBody(1L, hoursFromNow(1), hoursFromNow(3)))
                .andExpect(status().isForbidden());
    }

    // ---------- tiện ích: dựng dữ liệu qua API của examdesign ----------

    private long usableExam(long teacherId, String title) throws Exception {
        long exam = createExam(teacherId, title);
        long question = createQuestion(teacherId, "Câu hỏi");
        perform(put("/api/design/exams/" + exam + "/questions"), teacher(teacherId),
                Map.of("questionIds", List.of(question)))
                .andExpect(status().isOk());
        return exam;
    }

    private long createExam(long teacherId, String title) throws Exception {
        return idOf(perform(post("/api/design/exams"), teacher(teacherId),
                Map.of("title", title, "durationMinutes", 15)));
    }

    private long createQuestion(long teacherId, String content) throws Exception {
        return idOf(perform(post("/api/design/questions"), teacher(teacherId),
                Map.of("content", content, "options", List.of("A", "B"), "correctOption", 0)));
    }

    private void replaceQuestions(long exam, long... questionIds) throws Exception {
        perform(put("/api/design/exams/" + exam + "/questions"), teacher(TEACHER_ID),
                Map.of("questionIds", java.util.Arrays.stream(questionIds).boxed().toList()))
                .andExpect(status().isOk());
    }

    private long createSession(long exam) throws Exception {
        return createSession(exam, hoursFromNow(1), hoursFromNow(3));
    }

    private long createSession(long exam, Instant startAt, Instant endAt) throws Exception {
        return idOf(perform(post(URL), teacher(TEACHER_ID), sessionBody(exam, startAt, endAt)));
    }

    private long createSessionAs(long teacherId, long exam) throws Exception {
        return idOf(perform(post(URL), teacher(teacherId), sessionBody(exam, hoursFromNow(1), hoursFromNow(3))));
    }

    private long idOf(ResultActions created) throws Exception {
        return json(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, RequestPostProcessor user, Object body)
            throws Exception {
        request.with(user);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(String content) {
        return objectMapper.readTree(content);
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

    private static void expectInvalidState(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATE"));
    }
}
