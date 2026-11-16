package vn.exampractice.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình JWT đọc từ {@code app.jwt} trong application.yaml (giá trị thật lấy từ biến môi
 * trường, xem .env.example).
 *
 * @param secret       khóa ký token, tối thiểu 32 byte (256 bit); thiếu hoặc quá ngắn thì ứng
 *                     dụng không khởi động
 * @param expirationMs thời gian sống của token, tính bằng mili giây
 */
@ConfigurationProperties("app.jwt")
record JwtProperties(String secret, long expirationMs) {
}
