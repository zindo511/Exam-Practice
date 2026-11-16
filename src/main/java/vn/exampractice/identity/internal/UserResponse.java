package vn.exampractice.identity.internal;

import java.time.Instant;

/**
 * Thông tin user trả về sau khi đăng ký. Không có mật khẩu đã băm.
 */
record UserResponse(Long id, String username, String fullName, Role role, Instant createdAt) {

    static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                user.getCreatedAt());
    }
}
