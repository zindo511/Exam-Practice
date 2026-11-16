package vn.exampractice.identity.internal;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import vn.exampractice.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm tra {@link UserAccountRepository} trên PostgreSQL thật (ADR-0014): entity lưu và đọc lại
 * đúng, truy vấn theo username, và ràng buộc {@code UNIQUE} của migration.
 *
 * <p>{@code @DataJpaTest} chỉ khởi động phần JPA và Flyway; mỗi test chạy trong một transaction
 * được rollback khi kết thúc, nên các test không ảnh hưởng nhau.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class UserAccountRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-16T08:00:00Z");
    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234";

    @Autowired
    private UserAccountRepository repository;

    @Test
    void saves_and_reads_back_all_fields() {
        UserAccount saved = repository.saveAndFlush(
                UserAccount.register("giaovien01", HASH, "Lê Văn C", Role.TEACHER, NOW));

        UserAccount found = repository.findByUsername("giaovien01").orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId()).isNotNull();
        assertThat(found.getPasswordHash()).isEqualTo(HASH);
        assertThat(found.getFullName()).isEqualTo("Lê Văn C");
        assertThat(found.getRole()).isEqualTo(Role.TEACHER);
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void finds_by_normalized_username_only() {
        repository.saveAndFlush(UserAccount.register("HocSinh", HASH, "A", Role.STUDENT, NOW));

        assertThat(repository.existsByUsername("hocsinh")).isTrue();
        assertThat(repository.findByUsername(UserAccount.normalizeUsername(" HOCSINH "))).isPresent();
        assertThat(repository.existsByUsername("khong-co")).isFalse();
    }

    @Test
    void database_rejects_duplicate_username() {
        repository.saveAndFlush(UserAccount.register("trung", HASH, "A", Role.STUDENT, NOW));

        assertThatThrownBy(() -> repository.saveAndFlush(
                UserAccount.register("Trung", HASH, "B", Role.TEACHER, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
