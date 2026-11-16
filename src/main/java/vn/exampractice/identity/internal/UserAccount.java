package vn.exampractice.identity.internal;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tài khoản trong bảng {@code identity_user} (database.md mục 3).
 * Đặt tên UserAccount để không trùng {@code User} của Spring Security.
 *
 * <p>Không có setter (ADR-0008): tạo mới qua {@link #register}. MVP chưa có thao tác đổi thông tin
 * tài khoản; khi cần thì thêm hàm hành vi (ví dụ {@code changePassword}).
 *
 * <p>Username được chuẩn hóa (bỏ khoảng trắng hai đầu, chữ thường) ngay khi tạo, để
 * {@code Hoc.Sinh} và {@code hoc.sinh} không thành hai tài khoản và đăng nhập không phân biệt
 * hoa thường.
 */
@Entity
@Table(name = "identity_user")
class UserAccount {

    static final int USERNAME_MAX_LENGTH = 50;
    static final int FULL_NAME_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = USERNAME_MAX_LENGTH)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = FULL_NAME_MAX_LENGTH)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Chỉ dành cho JPA. */
    protected UserAccount() {
    }

    private UserAccount(String username, String passwordHash, String fullName, Role role, Instant createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.createdAt = createdAt;
    }

    /**
     * Tạo tài khoản mới. Định dạng đầu vào đã được kiểm tra ở DTO request; ở đây chỉ chặn dữ liệu
     * rỗng để entity không bao giờ ở trạng thái sai, dù được gọi từ đâu.
     *
     * @param passwordHash mật khẩu <b>đã băm</b> (BCrypt), không bao giờ là mật khẩu gốc
     * @param now          thời điểm tạo, truyền vào để test kiểm soát được thời gian
     */
    static UserAccount register(String username, String passwordHash, String fullName, Role role, Instant now) {
        return new UserAccount(
                normalizeUsername(username),
                requireText(passwordHash, "passwordHash"),
                requireText(fullName, "fullName").strip(),
                Objects.requireNonNull(role, "role"),
                Objects.requireNonNull(now, "now"));
    }

    /** Dùng chung cho đăng ký và đăng nhập, để hai nơi chuẩn hóa giống nhau. */
    static String normalizeUsername(String username) {
        return requireText(username, "username").strip().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    Long getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getFullName() {
        return fullName;
    }

    Role getRole() {
        return role;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
