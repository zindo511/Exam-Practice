package vn.exampractice.identity.internal;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.exampractice.shared.security.AuthenticatedUser;

/**
 * Tạo token khi đăng nhập và đọc token của mỗi request.
 *
 * <p>Token chứa claim {@code userId} và {@code role} (ADR-0015), ký bằng HMAC-SHA với
 * {@code app.jwt.secret}. Server không lưu token: token hợp lệ khi chữ ký đúng và chưa hết hạn.
 */
@Component
class JwtService {

    static final String CLAIM_USER_ID = "userId";
    static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration expiration;
    private final Clock clock;
    private final JwtParser parser;

    @Autowired
    JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** Cho test truyền đồng hồ cố định để kiểm tra token hết hạn. */
    JwtService(JwtProperties properties, Clock clock) {
        this.key = signingKey(properties.secret());
        this.expiration = Duration.ofMillis(properties.expirationMs());
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    /** Secret thiếu hoặc quá ngắn thì báo lỗi ngay khi khởi động, thay vì lúc đăng nhập. */
    private static SecretKey signingKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) chưa được cấu hình");
        }
        try {
            return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } catch (WeakKeyException e) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) phải dài tối thiểu 32 byte; tạo bằng: openssl rand -base64 48", e);
        }
    }

    /** Tạo token cho user vừa đăng nhập. */
    String issue(Long userId, Role role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /** Thời gian sống của token tính bằng giây, để trả cho client cùng token. */
    long expirationSeconds() {
        return expiration.toSeconds();
    }

    /**
     * Đọc token. Trả rỗng khi token sai chữ ký, sai định dạng, hết hạn hoặc thiếu claim.
     *
     * <p>Không ném lỗi: filter để request đi tiếp như chưa đăng nhập, và Security tự trả 401 nếu
     * URL cần đăng nhập.
     */
    Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Long userId = claims.get(CLAIM_USER_ID, Long.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            if (userId == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(userId, role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
