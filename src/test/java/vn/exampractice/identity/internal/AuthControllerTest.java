package vn.exampractice.identity.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.exampractice.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test cho đăng ký và đăng nhập, chạy toàn bộ ứng dụng trên PostgreSQL thật
 * (Testcontainers): controller, validation, service, BCrypt, database, JWT và Security.
 *
 * <p>Mỗi test chạy trong một transaction được rollback khi kết thúc, nên các test không ảnh
 * hưởng nhau.
 */
@SpringBootTest(properties = "app.jwt.secret=khoa-bi-mat-chi-dung-cho-test-dai-hon-32-byte-0123456789")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerTest {

    private static final String PASSWORD = "mat-khau-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository repository;

    @Test
    void register_creates_user_with_hashed_password() throws Exception {
        register("HocSinh01", PASSWORD, "Nguyễn Văn A", "STUDENT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username").value("hocsinh01"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String storedHash = repository.findByUsername("hocsinh01").orElseThrow().getPasswordHash();
        assertThat(storedHash).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void register_with_taken_username_ignoring_case_is_409() throws Exception {
        register("giaovien", PASSWORD, "A", "TEACHER").andExpect(status().isCreated());

        register("GiaoVien", PASSWORD, "B", "TEACHER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void register_with_invalid_body_is_400_with_errors_per_field() throws Exception {
        register("a b", "ngan", "", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("username")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("fullName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("role")));
    }

    @Test
    void register_with_unknown_role_is_malformed_request() throws Exception {
        register("user01", PASSWORD, "A", "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void login_returns_token_that_authenticates_later_requests() throws Exception {
        register("hocsinh02", PASSWORD, "A", "STUDENT").andExpect(status().isCreated());

        String body = login("HOCSINH02", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("accessToken").asString();

        // Không có endpoint thật nào của /api/exam: đã đăng nhập thì qua được Security và nhận 404,
        // chưa đăng nhập thì bị chặn với 401.
        mockMvc.perform(get("/api/exam/khong-ton-tai").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/exam/khong-ton-tai"))
                .andExpect(status().isUnauthorized());
        // Học sinh không vào được khu vực của giáo viên.
        mockMvc.perform(get("/api/design/khong-ton-tai").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_with_wrong_password_or_unknown_user_gives_same_401() throws Exception {
        register("hocsinh03", PASSWORD, "A", "STUDENT").andExpect(status().isCreated());

        JsonNode wrongPassword = problem(login("hocsinh03", "sai-mat-khau").andExpect(status().isUnauthorized()));
        JsonNode unknownUser = problem(login("khong-co-ai", PASSWORD).andExpect(status().isUnauthorized()));

        assertThat(wrongPassword.get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknownUser.get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPassword.get("detail")).isEqualTo(unknownUser.get("detail"));
    }

    @Test
    void login_with_blank_fields_is_400() throws Exception {
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    private ResultActions register(String username, String password, String fullName, String role) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("username", username);
        body.put("password", password);
        body.put("fullName", fullName);
        body.put("role", role);
        return postJson("/api/auth/register", body);
    }

    private ResultActions login(String username, String password) throws Exception {
        return postJson("/api/auth/login", java.util.Map.of("username", username, "password", password));
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode problem(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
