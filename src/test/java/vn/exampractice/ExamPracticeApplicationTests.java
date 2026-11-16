package vn.exampractice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Ứng dụng khởi động được trên PostgreSQL thật: migration Flyway chạy được và
 * entity khớp schema (Hibernate validate).
 *
 * <p>Tự cấp {@code app.jwt.secret} để chạy được trên máy không có file .env; secret này chỉ dùng
 * cho test.
 */
@SpringBootTest(properties = "app.jwt.secret=khoa-bi-mat-chi-dung-cho-test-dai-hon-32-byte-0123456789")
@Import(TestcontainersConfiguration.class)
class ExamPracticeApplicationTests {

    @Test
    void contextLoads() {
    }

}
