package vn.exampractice.examination.internal;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;
import vn.exampractice.TestcontainersConfiguration;
import vn.exampractice.support.TestAuth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.exampractice.support.TestAuth.student;

/**
 * Giới hạn nhập sai mã (ADR-0010), kiểm tra với transaction thật.
 *
 * <p>Class này cố ý <b>không</b> có {@code @Transactional}: mỗi request tự commit hoặc rollback như khi
 * chạy thật. Nếu test chạy trong một transaction chung, lần sai vẫn nhìn thấy được trong transaction
 * đó dù sắp bị rollback, nên không phân biệt được lần sai đã thực sự được lưu hay chưa (đã thử bỏ
 * {@code REQUIRES_NEW} trong {@link JoinFailureRecorder}: test trong transaction chung vẫn qua).
 * Dữ liệu tự dọn sau mỗi test.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JoinFailureLimitTest {

    private static final AtomicLong NEXT_STUDENT = new AtomicLong(System.currentTimeMillis() + 1_000_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private final long student = NEXT_STUDENT.incrementAndGet();

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM exam_join_failure WHERE student_id = ?", student);
    }

    @Test
    void wrong_code_is_saved_although_the_request_is_rolled_back() throws Exception {
        join("ZZZZZZ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_CODE"));

        assertThat(savedFailures()).isEqualTo(1);
    }

    @Test
    void sixth_attempt_after_five_wrong_codes_is_blocked_and_not_recorded() throws Exception {
        for (int i = 0; i < JoinAttemptLimiter.MAX_FAILURES; i++) {
            join("ZZZZZZ").andExpect(status().isConflict());
        }

        join("ZZZZZZ")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCESS_CODE_ATTEMPTS_EXCEEDED"));

        assertThat(savedFailures()).isEqualTo(JoinAttemptLimiter.MAX_FAILURES);
    }

    private ResultActions join(String code) throws Exception {
        return mockMvc.perform(post("/api/exam/sessions/join").with(student(student))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("accessCode", code))));
    }

    private int savedFailures() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM exam_join_failure WHERE student_id = ?",
                Integer.class, student);
    }
}
