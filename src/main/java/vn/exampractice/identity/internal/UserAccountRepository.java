package vn.exampractice.identity.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Truy vấn và lưu {@link UserAccount}.
 *
 * <p>Username trong database luôn ở dạng đã chuẩn hóa, nên tham số {@code username} phải đi qua
 * {@link UserAccount#normalizeUsername(String)} trước khi gọi, nếu không {@code "HocSinh"} sẽ không
 * tìm thấy {@code "hocsinh"}.
 *
 * <p>Lấy nhiều user theo danh sách ID (cho {@code IdentityApi}) dùng {@code findAllById} có sẵn của
 * {@link JpaRepository}: một câu {@code WHERE id IN (...)} (README 6.8).
 */
interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** Dùng khi đăng nhập. */
    Optional<UserAccount> findByUsername(String username);

    /** Dùng khi đăng ký, để báo username đã tồn tại trước khi lưu. */
    boolean existsByUsername(String username);
}
