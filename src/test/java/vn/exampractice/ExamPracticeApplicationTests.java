package vn.exampractice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Ứng dụng khởi động được trên PostgreSQL thật: migration Flyway chạy được và
 * entity khớp schema (Hibernate validate).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExamPracticeApplicationTests {

    @Test
    void contextLoads() {
    }

}
