package vn.exampractice.identity.internal;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test cho luật trong entity {@link UserAccount}. Không cần Spring hay database.
 */
class UserAccountTest {

    private static final Instant NOW = Instant.parse("2026-11-16T08:00:00Z");
    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234";

    @Test
    void register_keeps_given_data() {
        UserAccount user = UserAccount.register("hocsinh01", HASH, "Nguyễn Văn A", Role.STUDENT, NOW);

        assertThat(user.getId()).isNull();
        assertThat(user.getUsername()).isEqualTo("hocsinh01");
        assertThat(user.getPasswordHash()).isEqualTo(HASH);
        assertThat(user.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(user.getRole()).isEqualTo(Role.STUDENT);
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void register_normalizes_username_and_trims_full_name() {
        UserAccount user = UserAccount.register("  Hoc.Sinh  ", HASH, "  Trần Thị B ", Role.TEACHER, NOW);

        assertThat(user.getUsername()).isEqualTo("hoc.sinh");
        assertThat(user.getFullName()).isEqualTo("Trần Thị B");
    }

    @Test
    void register_rejects_blank_text() {
        assertThatThrownBy(() -> UserAccount.register(" ", HASH, "A", Role.STUDENT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.register("a", null, "A", Role.STUDENT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.register("a", HASH, "", Role.STUDENT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_rejects_missing_role_or_time() {
        assertThatThrownBy(() -> UserAccount.register("a", HASH, "A", null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UserAccount.register("a", HASH, "A", Role.STUDENT, null))
                .isInstanceOf(NullPointerException.class);
    }
}
