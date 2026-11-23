package vn.exampractice.examination.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.exampractice.TestcontainersConfiguration;
import vn.exampractice.support.TestAuth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.exampractice.support.TestAuth.student;
import static vn.exampractice.support.TestAuth.teacher;

/**
 * Thao tác ghi song song lên cùng một bài làm (ADR-0017), với transaction thật.
 *
 * <p>Class này cố ý <b>không</b> có {@code @Transactional}: mỗi request tự commit, các request chạy
 * song song thật trên nhiều luồng. Dữ liệu dùng một giáo viên riêng và được xóa sau mỗi test.
 */
@SpringBootTest(properties = TestAuth.JWT_SECRET_PROPERTY)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AttemptConcurrencyTest {

    private static final AtomicLong NEXT_ID = new AtomicLong(System.currentTimeMillis() + 9_000_000);
    private static final int ROUNDS = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private final long teacherId = NEXT_ID.incrementAndGet();
    private final List<Long> sessions = new ArrayList<>();

    /** Hai request lưu đáp án đầu tiên của cùng một câu: cả hai thành công, chỉ có một dòng đáp án. */
    @Test
    void simultaneous_first_answers_to_one_question_both_succeed() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long studentId = NEXT_ID.incrementAndGet();
            JsonNode attempt = startAttempt(studentId);
            long attemptId = attempt.get("id").asLong();
            long questionId = attempt.get("questions").get(0).get("questionId").asLong();

            List<Integer> statuses = runTogether(
                    () -> answer(studentId, attemptId, questionId, 0),
                    () -> answer(studentId, attemptId, questionId, 1));

            assertThat(statuses).as("round %d", round).containsOnly(204);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exam_answer WHERE attempt_id = ?",
                    Integer.class, attemptId)).isEqualTo(1);
        }
    }

    /**
     * Nộp bài đúng lúc đang đổi đáp án: hoặc đáp án đổi trước rồi được chấm, hoặc bị từ chối vì bài đã
     * nộp. Không bao giờ có chuyện đáp án đổi sau khi đã chấm (điểm lệch với đáp án đã lưu).
     */
    @Test
    void submit_racing_an_answer_change_never_leaves_score_out_of_date() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long studentId = NEXT_ID.incrementAndGet();
            JsonNode attempt = startAttempt(studentId);
            long attemptId = attempt.get("id").asLong();
            long questionId = attempt.get("questions").get(0).get("questionId").asLong();
            assertThat(answer(studentId, attemptId, questionId, 1)).isEqualTo(204);

            List<Integer> statuses = runTogether(
                    () -> perform(post("/api/exam/attempts/" + attemptId + "/submit"), student(studentId), null)
                            .andReturn().getResponse().getStatus(),
                    () -> answer(studentId, attemptId, questionId, 0));

            assertThat(statuses.get(0)).as("submit, round %d", round).isEqualTo(200);
            assertThat(statuses.get(1)).as("answer, round %d", round).isIn(204, 409);
            int storedCorrect = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM exam_answer a
                    JOIN exam_session_question q ON q.id = a.session_question_id
                    WHERE a.attempt_id = ? AND a.selected_option = q.correct_option""", Integer.class, attemptId);
            int gradedCorrect = jdbc.queryForObject("SELECT correct_count FROM exam_attempt WHERE id = ?",
                    Integer.class, attemptId);
            assertThat(gradedCorrect).as("round %d", round).isEqualTo(storedCorrect);
        }
    }

    @AfterEach
    void cleanUp() {
        for (long session : sessions) {
            jdbc.update("DELETE FROM exam_answer WHERE attempt_id IN (SELECT id FROM exam_attempt WHERE session_id = ?)",
                    session);
            jdbc.update("DELETE FROM exam_attempt WHERE session_id = ?", session);
            jdbc.update("DELETE FROM exam_participant WHERE session_id = ?", session);
            jdbc.update("DELETE FROM exam_session_question WHERE session_id = ?", session);
            jdbc.update("DELETE FROM exam_session WHERE id = ?", session);
        }
        jdbc.update("DELETE FROM design_exam_question WHERE exam_id IN (SELECT id FROM design_exam WHERE teacher_id = ?)",
                teacherId);
        jdbc.update("DELETE FROM design_exam WHERE teacher_id = ?", teacherId);
        jdbc.update("DELETE FROM design_question WHERE teacher_id = ?", teacherId);
    }

    // ---------- tiện ích ----------

    @SafeVarargs
    private List<Integer> runTogether(Callable<Integer>... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private int answer(long studentId, long attemptId, long questionId, int option) throws Exception {
        return perform(put("/api/exam/attempts/" + attemptId + "/answers/" + questionId), student(studentId),
                Map.of("selectedOption", option)).andReturn().getResponse().getStatus();
    }

    /** Kỳ thi đang mở với đề 1 câu (đáp án đúng 0), học sinh đã tham gia và bắt đầu làm bài. */
    private JsonNode startAttempt(long studentId) throws Exception {
        long question = idOf(perform(post("/api/design/questions"), teacher(teacherId),
                Map.of("content", "Câu hỏi", "options", List.of("A", "B"), "correctOption", 0)));
        long exam = idOf(perform(post("/api/design/exams"), teacher(teacherId),
                Map.of("title", "Kiểm tra", "durationMinutes", 15)));
        perform(put("/api/design/exams/" + exam + "/questions"), teacher(teacherId),
                Map.of("questionIds", List.of(question))).andExpect(status().isOk());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("examId", exam);
        body.put("startAt", Instant.now().plus(Duration.ofHours(1)).toString());
        body.put("endAt", Instant.now().plus(Duration.ofHours(3)).toString());
        body.put("resultVisibility", "SCORE");
        long session = idOf(perform(post("/api/exam/sessions"), teacher(teacherId), body));
        sessions.add(session);
        String code = objectMapper.readTree(perform(get("/api/exam/sessions/" + session), teacher(teacherId), null)
                .andReturn().getResponse().getContentAsString()).get("accessCode").asString();
        perform(post("/api/exam/sessions/join"), student(studentId), Map.of("accessCode", code))
                .andExpect(status().isOk());
        perform(post("/api/exam/sessions/" + session + "/open"), teacher(teacherId), null).andExpect(status().isOk());
        String json = perform(post("/api/exam/sessions/" + session + "/attempts"), student(studentId), null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
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
