package vn.exampractice.examdesign.internal;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.exampractice.TestcontainersConfiguration;
import vn.exampractice.support.TestAuth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.exampractice.support.TestAuth.student;
import static vn.exampractice.support.TestAuth.teacher;

/**
 * Integration test cho {@code /api/design/exams} trên PostgreSQL thật: controller, validation,
 * service, database và phân quyền.
 *
 * <p>Mỗi test chạy trong một transaction được rollback khi kết thúc. Câu hỏi được tạo thẳng qua
 * repository vì API câu hỏi đã có test riêng.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ExamControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long OTHER_TEACHER_ID = 2L;
    private static final String URL = "/api/design/exams";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void create_returns_201_with_location_and_summary() throws Exception {
        var response = perform(post(URL), teacher(TEACHER_ID), examBody("  Kiểm tra 15 phút ", 15))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Kiểm tra 15 phút"))
                .andExpect(jsonPath("$.durationMinutes").value(15))
                .andExpect(jsonPath("$.questionCount").value(0))
                .andExpect(jsonPath("$.questions").doesNotExist())
                .andReturn().getResponse();

        long id = objectMapper.readTree(response.getContentAsString()).get("id").asLong();
        assertThat(response.getHeader("Location")).endsWith(URL + "/" + id);
    }

    @Test
    void create_with_invalid_body_returns_errors_per_field() throws Exception {
        perform(post(URL), teacher(TEACHER_ID), examBody(" ", 301))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("title")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("durationMinutes")));
    }

    @Test
    void replace_questions_keeps_given_order_and_detail_shows_content() throws Exception {
        long exam = createExam(TEACHER_ID, "Đề");
        long q1 = question(TEACHER_ID, "Câu một");
        long q2 = question(TEACHER_ID, "Câu hai");

        perform(put(URL + "/" + exam + "/questions"), teacher(TEACHER_ID), questionsBody(q2, q1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionCount").value(2))
                .andExpect(jsonPath("$.questions[0].position").value(1))
                .andExpect(jsonPath("$.questions[0].questionId").value(q2))
                .andExpect(jsonPath("$.questions[0].content").value("Câu hai"))
                .andExpect(jsonPath("$.questions[1].questionId").value(q1));

        perform(get(URL + "/" + exam), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[1].content").value("Câu một"))
                .andExpect(jsonPath("$.questions[1].options", hasSize(2)))
                .andExpect(jsonPath("$.questions[1].correctOption").value(0));
    }

    @Test
    void replace_questions_again_reorders_and_removes() throws Exception {
        long exam = createExam(TEACHER_ID, "Đề");
        long q1 = question(TEACHER_ID, "Câu một");
        long q2 = question(TEACHER_ID, "Câu hai");
        long q3 = question(TEACHER_ID, "Câu ba");
        perform(put(URL + "/" + exam + "/questions"), teacher(TEACHER_ID), questionsBody(q1, q2, q3))
                .andExpect(status().isOk());

        perform(put(URL + "/" + exam + "/questions"), teacher(TEACHER_ID), questionsBody(q3, q1))
                .andExpect(status().isOk());

        perform(get(URL + "/" + exam), teacher(TEACHER_ID), null)
                .andExpect(jsonPath("$.questionCount").value(2))
                .andExpect(jsonPath("$.questions[0].questionId").value(q3))
                .andExpect(jsonPath("$.questions[1].questionId").value(q1));
    }

    @Test
    void question_of_another_teacher_or_archived_is_not_available() throws Exception {
        long exam = createExam(TEACHER_ID, "Đề");
        long own = question(TEACHER_ID, "Câu của mình");
        long foreign = question(OTHER_TEACHER_ID, "Câu của người khác");
        long archived = question(TEACHER_ID, "Câu đã lưu trữ");
        questionRepository.findById(archived).orElseThrow().archive(Instant.now());

        perform(put(URL + "/" + exam + "/questions"), teacher(TEACHER_ID), questionsBody(own, foreign, archived, 999_999L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.detail", containsString(foreign + ", " + archived + ", 999999")));

        perform(get(URL + "/" + exam), teacher(TEACHER_ID), null)
                .andExpect(jsonPath("$.questionCount").value(0));
    }

    @Test
    void duplicate_question_is_rejected() throws Exception {
        long exam = createExam(TEACHER_ID, "Đề");
        long q1 = question(TEACHER_ID, "Câu một");

        perform(put(URL + "/" + exam + "/questions"), teacher(TEACHER_ID), questionsBody(q1, q1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("withoutDuplicates"));
    }

    @Test
    void list_shows_own_active_exams_newest_first_with_question_count() throws Exception {
        long first = createExam(TEACHER_ID, "Đề thứ nhất");
        long second = createExam(TEACHER_ID, "Đề thứ hai");
        long archived = createExam(TEACHER_ID, "Đề đã lưu trữ");
        createExam(OTHER_TEACHER_ID, "Đề của giáo viên khác");
        long q1 = question(TEACHER_ID, "Câu một");
        long q2 = question(TEACHER_ID, "Câu hai");
        perform(put(URL + "/" + first + "/questions"), teacher(TEACHER_ID), questionsBody(q1, q2))
                .andExpect(status().isOk());
        perform(delete(URL + "/" + archived), teacher(TEACHER_ID), null).andExpect(status().isNoContent());

        perform(get(URL), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[0].questionCount").value(0))
                .andExpect(jsonPath("$.content[1].id").value(first))
                .andExpect(jsonPath("$.content[1].questionCount").value(2));
    }

    @Test
    void list_filters_by_title_ignoring_case() throws Exception {
        createExam(TEACHER_ID, "Kiểm tra GIỮA KỲ");
        createExam(TEACHER_ID, "Thi cuối kỳ");

        perform(get(URL).param("keyword", "giữa kỳ"), teacher(TEACHER_ID), null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Kiểm tra GIỮA KỲ"));
    }

    @Test
    void update_changes_info_and_keeps_questions() throws Exception {
        long exam = createExam(TEACHER_ID, "Đề cũ");
        long q1 = question(TEACHER_ID, "Câu một");
        perform(put(URL + "/" + exam + "/questions"), teacher(TEACHER_ID), questionsBody(q1))
                .andExpect(status().isOk());

        perform(put(URL + "/" + exam), teacher(TEACHER_ID), examBody("Đề mới", 60))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(exam))
                .andExpect(jsonPath("$.title").value("Đề mới"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.questionCount").value(1))
                .andExpect(jsonPath("$.questions").doesNotExist());
    }

    @Test
    void exam_of_another_teacher_is_not_found() throws Exception {
        long exam = createExam(OTHER_TEACHER_ID, "Đề của người khác");

        expectNotFound(perform(get(URL + "/" + exam), teacher(TEACHER_ID), null));
        expectNotFound(perform(put(URL + "/" + exam), teacher(TEACHER_ID), examBody("Sửa trộm", 10)));
        expectNotFound(perform(put(URL + "/" + exam + "/questions"), teacher(TEACHER_ID), questionsBody()));
        expectNotFound(perform(delete(URL + "/" + exam), teacher(TEACHER_ID), null));
    }

    @Test
    void archived_exam_is_not_found_anymore() throws Exception {
        long exam = createExam(TEACHER_ID, "Đề sẽ lưu trữ");

        perform(delete(URL + "/" + exam), teacher(TEACHER_ID), null).andExpect(status().isNoContent());

        expectNotFound(perform(get(URL + "/" + exam), teacher(TEACHER_ID), null));
        expectNotFound(perform(put(URL + "/" + exam + "/questions"), teacher(TEACHER_ID), questionsBody()));
    }

    @Test
    void students_cannot_use_exams() throws Exception {
        perform(get(URL), student(3L), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---------- tiện ích ----------

    private long createExam(long teacherId, String title) throws Exception {
        String json = perform(post(URL), teacher(teacherId), examBody(title, 15))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private long question(long teacherId, String content) {
        return questionRepository.save(Question.create(teacherId, content, List.of("A", "B"), 0, Instant.now()))
                .getId();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, RequestPostProcessor user, Object body)
            throws Exception {
        request.with(user);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private static Map<String, Object> examBody(String title, int durationMinutes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("durationMinutes", durationMinutes);
        return body;
    }

    private static Map<String, Object> questionsBody(Long... questionIds) {
        return Map.of("questionIds", List.of(questionIds));
    }

    private static void expectNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAM_NOT_FOUND"));
    }
}
