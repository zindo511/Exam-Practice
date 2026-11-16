package vn.exampractice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL chạy trong Docker cho các test cần database (ADR-0014).
 *
 * <p>{@code @ServiceConnection} tự trỏ datasource của Spring vào container, nên test không
 * đụng tới database trên máy. Cần Docker đang chạy.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
