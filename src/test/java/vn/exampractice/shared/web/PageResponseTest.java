package vn.exampractice.shared.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra JSON của {@link PageResponse} (ADR-0009) và cấu hình phân trang trong
 * application.yaml: size mặc định 20, tối đa 100, page bắt đầu từ 0.
 *
 * <p>Tắt filter của Security vì test này không liên quan đến xác thực.
 */
@WebMvcTest(controllers = PageResponseTest.FakePageController.class)
@Import(PageResponseTest.FakePageController.class)
@AutoConfigureMockMvc(addFilters = false)
class PageResponseTest {

    private static final long TOTAL_ELEMENTS = 45;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns_page_fields_and_uses_default_size_when_not_given() throws Exception {
        mockMvc.perform(get("/fake/pages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0]").value("a"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(TOTAL_ELEMENTS))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void page_starts_from_zero() throws Exception {
        mockMvc.perform(get("/fake/pages").param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalPages").value(5));
    }

    @Test
    void size_above_maximum_is_capped_at_100() throws Exception {
        mockMvc.perform(get("/fake/pages").param("size", "500"))
                .andExpect(jsonPath("$.size").value(100));
    }

    @RestController
    static class FakePageController {

        @GetMapping("/fake/pages")
        PageResponse<String> pages(Pageable pageable) {
            return PageResponse.from(new PageImpl<>(List.of("a", "b"), pageable, TOTAL_ELEMENTS));
        }
    }
}
