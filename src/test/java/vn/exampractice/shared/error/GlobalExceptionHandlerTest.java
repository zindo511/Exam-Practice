package vn.exampractice.shared.error;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra mỗi loại exception được chuyển thành đúng status, {@code code} và định dạng
 * {@code ProblemDetail} (README 6.6), qua một controller giả chỉ có trong test.
 *
 * <p>Dùng MockMvc standalone: không khởi động Spring context, không cần database hay Security.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void business_exception_uses_status_code_and_default_message_of_its_error_code() throws Exception {
        expectProblem(mockMvc.perform(get("/fake/business")), HttpStatus.CONFLICT, "FAKE_RULE_BROKEN", "/fake/business")
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.detail").value("Luật giả bị vi phạm"));
    }

    @Test
    void business_exception_can_override_detail() throws Exception {
        expectProblem(mockMvc.perform(get("/fake/business-detail")), HttpStatus.CONFLICT, "FAKE_RULE_BROKEN", "/fake/business-detail")
                .andExpect(jsonPath("$.detail").value("Câu cụ thể hơn"));
    }

    @Test
    void invalid_request_body_returns_errors_per_field() throws Exception {
        ResultActions result = mockMvc.perform(post("/fake/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\", \"options\": [\"A\"]}"));

        expectProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "/fake/body")
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("Không được để trống"))
                .andExpect(jsonPath("$.errors[?(@.field == 'options')].message").value("Cần ít nhất 2 lựa chọn"));
    }

    @Test
    void invalid_request_parameter_returns_errors_per_parameter() throws Exception {
        expectProblem(mockMvc.perform(get("/fake/param").param("size", "0")), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "/fake/param")
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("Phải lớn hơn hoặc bằng 1"));
    }

    @Test
    void broken_json_is_malformed_request() throws Exception {
        ResultActions result = mockMvc.perform(post("/fake/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ không phải json"));

        expectProblem(result, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "/fake/body");
    }

    @Test
    void path_variable_of_wrong_type_is_malformed_request() throws Exception {
        expectProblem(mockMvc.perform(get("/fake/items/abc")), HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "/fake/items/abc")
                .andExpect(jsonPath("$.detail").value("Tham số 'id' không đúng kiểu dữ liệu"));
    }

    @Test
    void missing_required_parameter_is_malformed_request() throws Exception {
        expectProblem(mockMvc.perform(get("/fake/param")), HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "/fake/param");
    }

    @Test
    void unknown_url_is_not_found() throws Exception {
        expectProblem(mockMvc.perform(get("/khong-ton-tai")), HttpStatus.NOT_FOUND, "NOT_FOUND", "/khong-ton-tai");
    }

    @Test
    void wrong_http_method_is_method_not_allowed_with_allow_header() throws Exception {
        expectProblem(mockMvc.perform(delete("/fake/business")), HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "/fake/business")
                .andExpect(header().string("Allow", containsString("GET")));
    }

    @Test
    void unsupported_content_type_is_unsupported_media_type() throws Exception {
        ResultActions result = mockMvc.perform(post("/fake/body")
                .contentType(MediaType.TEXT_PLAIN)
                .content("xin chào"));

        expectProblem(result, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "/fake/body");
    }

    @Test
    void access_denied_inside_controller_is_forbidden_not_internal_error() throws Exception {
        expectProblem(mockMvc.perform(get("/fake/forbidden")), HttpStatus.FORBIDDEN, "FORBIDDEN", "/fake/forbidden");
    }

    @Test
    void optimistic_locking_failure_is_concurrent_modification() throws Exception {
        expectProblem(mockMvc.perform(get("/fake/conflict")), HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "/fake/conflict");
    }

    @Test
    void unexpected_exception_is_internal_error_without_leaking_its_message() throws Exception {
        expectProblem(mockMvc.perform(get("/fake/crash")), HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "/fake/crash")
                .andExpect(jsonPath("$.detail", not(containsString("bí mật"))));
    }

    /** Các field bắt buộc của mọi response lỗi (README 6.6). */
    private static ResultActions expectProblem(ResultActions result, HttpStatus status, String code, String path)
            throws Exception {
        return result
                .andExpect(status().is(status.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status.value()))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.detail", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /** Mã lỗi giả, đóng vai enum mã lỗi riêng của một module. */
    enum FakeErrorCode implements ErrorCode {
        FAKE_RULE_BROKEN;

        @Override
        public String code() {
            return name();
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String defaultMessage() {
            return "Luật giả bị vi phạm";
        }
    }

    record FakeBody(
            @NotBlank(message = "Không được để trống") String name,
            @Size(min = 2, message = "Cần ít nhất 2 lựa chọn") List<String> options) {
    }

    @RestController
    static class FakeController {

        @GetMapping("/fake/business")
        void business() {
            throw new BusinessException(FakeErrorCode.FAKE_RULE_BROKEN);
        }

        @GetMapping("/fake/business-detail")
        void businessWithDetail() {
            throw new BusinessException(FakeErrorCode.FAKE_RULE_BROKEN, "Câu cụ thể hơn");
        }

        @PostMapping("/fake/body")
        void body(@Valid @RequestBody FakeBody body) {
        }

        @GetMapping("/fake/param")
        void param(@RequestParam @Min(value = 1, message = "Phải lớn hơn hoặc bằng 1") int size) {
        }

        @GetMapping("/fake/items/{id}")
        void item(@PathVariable Long id) {
        }

        @GetMapping("/fake/forbidden")
        void forbidden() {
            throw new AccessDeniedException("không đủ quyền");
        }

        @GetMapping("/fake/conflict")
        void conflict() {
            throw new ObjectOptimisticLockingFailureException("Attempt", 1L);
        }

        @GetMapping("/fake/crash")
        void crash() {
            throw new IllegalStateException("thông tin bí mật của server");
        }
    }
}
