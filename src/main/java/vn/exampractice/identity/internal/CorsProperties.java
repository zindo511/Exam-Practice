package vn.exampractice.identity.internal;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình CORS đọc từ {@code app.cors} trong application.yaml (giá trị thật lấy từ biến môi trường, xem
 * .env.example).
 *
 * @param allowedOrigins địa chỉ giao diện được gọi API từ trình duyệt; rỗng thì không bật CORS
 */
@ConfigurationProperties("app.cors")
record CorsProperties(List<String> allowedOrigins) {

    CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
