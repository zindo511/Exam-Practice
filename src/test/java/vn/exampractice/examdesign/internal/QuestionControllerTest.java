package vn.exampractice.examdesign.internal;

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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.exampractice.support.TestAuth.student;
import static vn.exampractice.support.TestAuth.teacher;

/**
 * Integration test cho {@code /api/design/questions} trên PostgreSQL thật: controller, validation,
 * service, database và phân quyền.
 *
 * <p>Mỗi test chạy trong một transaction được rollback khi kết thúc.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class QuestionControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long OTHER_TEACHER_ID = 2L;
    private static final String URL = "/api/design/questions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void create_returns_201_with_location_and_question() throws Exception {
        var response = perform(post(URL), teacher(TEACHER_ID), body(" Thủ đô của Việt Nam? ", List.of("Hà Nội", "Huế"), 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.content").value("Thủ đô của Việt Nam?"))
                .andExpect(jsonPath("$.options", hasSize(2)))
                .andExpect(jsonPath("$.correctOption").value(0))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andReturn().getResponse();

        long id = objectMapper.readTree(response.getContentAsString()).get("id").asLong();
        assertThat(response.getHeader("Location")).endsWith(URL + "/" + id);
    }

    @Test
    void create_with_invalid_body_returns_errors_per_field() throws Exception {
        perform(post(URL), teacher(TEACHER_ID), body("", List.of("Chỉ một"), 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("content")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("options")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("correctOptionInRange")));
    }

    @Test
    void blank_option_is_rejected() throws Exception {
        perform(post(URL), teacher(TEACHER_ID), body("Câu hỏi", List.of("A", " "), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("options[1]"));
    }

    @Test
    void list_shows_only_own_active_questions_newest_first() throws Exception {
        long first = create(TEACHER_ID, "Câu thứ nhất");
        long second = create(TEACHER_ID, "Câu thứ hai");
        long archived = create(TEACHER_ID, "Câu đã lưu trữ");
        create(OTHER_TEACHER_ID, "Câu của giáo viên khác");
        perform(delete(URL + "/" + archived), teacher(TEACHER_ID), null).andExpect(status().isNoContent());

        perform(get(URL), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first));
    }

    @Test
    void list_filters_by_keyword_ignoring_case_and_pages() throws Exception {
        create(TEACHER_ID, "Thủ đô của Pháp?");
        create(TEACHER_ID, "THỦ ĐÔ của Nhật?");
        create(TEACHER_ID, "2 + 2 = ?");

        perform(get(URL).param("keyword", "thủ đô").param("size", "1"), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void keyword_wildcards_are_treated_as_text() throws Exception {
        create(TEACHER_ID, "Giảm 50% giá");
        create(TEACHER_ID, "Không có ký tự đặc biệt");

        perform(get(URL).param("keyword", "%"), teacher(TEACHER_ID), null)
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void sorting_by_unknown_field_is_bad_request() throws Exception {
        perform(get(URL).param("sort", "khongCo,asc"), teacher(TEACHER_ID), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Không sắp xếp được theo 'khongCo'"));
    }

    @Test
    void get_and_update_own_question() throws Exception {
        long id = create(TEACHER_ID, "Câu cũ");

        perform(put(URL + "/" + id), teacher(TEACHER_ID), body("Câu mới", List.of("X", "Y", "Z"), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.content").value("Câu mới"));

        perform(get(URL + "/" + id), teacher(TEACHER_ID), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[2]").value("Z"))
                .andExpect(jsonPath("$.correctOption").value(2));
    }

    @Test
    void question_of_another_teacher_is_not_found() throws Exception {
        long id = create(OTHER_TEACHER_ID, "Câu của người khác");

        expectNotFound(perform(get(URL + "/" + id), teacher(TEACHER_ID), null));
        expectNotFound(perform(put(URL + "/" + id), teacher(TEACHER_ID), body("Sửa trộm", List.of("A", "B"), 0)));
        expectNotFound(perform(delete(URL + "/" + id), teacher(TEACHER_ID), null));
    }

    @Test
    void archived_question_is_not_found_anymore() throws Exception {
        long id = create(TEACHER_ID, "Câu sẽ lưu trữ");

        perform(delete(URL + "/" + id), teacher(TEACHER_ID), null).andExpect(status().isNoContent());

        expectNotFound(perform(get(URL + "/" + id), teacher(TEACHER_ID), null));
        expectNotFound(perform(delete(URL + "/" + id), teacher(TEACHER_ID), null));
    }

    @Test
    void students_cannot_use_question_bank() throws Exception {
        perform(get(URL), student(3L), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anonymous_request_is_unauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    // ---------- tiện ích ----------

    private long create(long teacherId, String content) throws Exception {
        String json = perform(post(URL), teacher(teacherId), body(content, List.of("A", "B"), 0))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
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

    private static Map<String, Object> body(String content, List<String> options, int correctOption) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("options", options);
        body.put("correctOption", correctOption);
        return body;
    }

    private static void expectNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    }
}
