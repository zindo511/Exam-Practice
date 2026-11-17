package vn.exampractice.shared.web;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.exampractice.TestcontainersConfiguration;
import vn.exampractice.support.TestAuth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra tài liệu OpenAPI mà Swagger UI dùng (README 6.9: endpoint hiển thị đúng trên Swagger UI):
 * có nút Authorize gửi JWT, và các tham số do server tự điền không hiện ra cho người gọi.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void declares_bearer_jwt_scheme_required_by_default() throws Exception {
        JsonNode docs = apiDocs();

        JsonNode scheme = docs.path("components").path("securitySchemes").path("bearerAuth");
        assertThat(scheme.path("type").asString()).isEqualTo("http");
        assertThat(scheme.path("scheme").asString()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asString()).isEqualTo("JWT");
        assertThat(docs.path("security").get(0).has("bearerAuth")).isTrue();
    }

    @Test
    void register_and_login_do_not_require_token() throws Exception {
        JsonNode paths = apiDocs().path("paths");

        for (String path : List.of("/api/auth/register", "/api/auth/login")) {
            JsonNode security = paths.path(path).path("post").path("security");
            assertThat(security.isArray() && security.isEmpty()).as("security of %s", path).isTrue();
        }
    }

    /** {@code @AuthenticationPrincipal} do server điền; {@code Pageable} tách thành page, size, sort. */
    @Test
    void list_endpoint_shows_paging_params_but_not_the_principal() throws Exception {
        JsonNode parameters = apiDocs().path("paths").path("/api/design/questions").path("get").path("parameters");

        List<String> names = new ArrayList<>();
        parameters.forEach(parameter -> names.add(parameter.path("name").asString()));
        assertThat(names).contains("keyword", "page", "size", "sort").doesNotContain("user", "pageable");
    }

    private JsonNode apiDocs() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }
}
