package vn.exampractice.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import vn.exampractice.shared.security.AuthenticatedUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test cho {@link JwtService}: tạo và đọc token, từ chối token hết hạn, sai chữ ký hoặc
 * sai định dạng. Không cần Spring.
 */
class JwtServiceTest {

    private static final String SECRET = "khoa-bi-mat-chi-dung-cho-test-dai-hon-32-byte-0123456789";
    private static final long ONE_HOUR_MS = 3_600_000;
    private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");

    private final JwtService service = serviceAt(NOW, SECRET);

    @Test
    void parses_back_the_user_id_and_role_it_issued() {
        String token = service.issue(42L, Role.TEACHER);

        assertThat(service.parse(token)).contains(new AuthenticatedUser(42L, "TEACHER"));
    }

    @Test
    void reports_expiration_in_seconds() {
        assertThat(service.expirationSeconds()).isEqualTo(3600);
    }

    @Test
    void rejects_expired_token() {
        String token = service.issue(42L, Role.STUDENT);
        JwtService twoHoursLater = serviceAt(NOW.plusSeconds(7200), SECRET);

        assertThat(twoHoursLater.parse(token)).isEmpty();
    }

    @Test
    void rejects_token_signed_with_another_secret() {
        String token = serviceAt(NOW, SECRET + "-khac").issue(42L, Role.TEACHER);

        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    void rejects_tampered_or_malformed_token() {
        String token = service.issue(42L, Role.STUDENT);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(service.parse(tampered)).isEmpty();
        assertThat(service.parse("khong-phai-jwt")).isEmpty();
        assertThat(service.parse("")).isEmpty();
    }

    @Test
    void refuses_to_start_with_missing_or_short_secret() {
        assertThatThrownBy(() -> serviceAt(NOW, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> serviceAt(NOW, "ngan-qua"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }

    private static JwtService serviceAt(Instant now, String secret) {
        return new JwtService(new JwtProperties(secret, ONE_HOUR_MS), Clock.fixed(now, ZoneOffset.UTC));
    }
}
