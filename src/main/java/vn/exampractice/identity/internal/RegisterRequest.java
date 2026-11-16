package vn.exampractice.identity.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /api/auth/register}.
 *
 * <p>Mật khẩu tối đa 72 ký tự vì BCrypt chỉ dùng 72 byte đầu; dài hơn thì phần thừa bị bỏ qua mà
 * người dùng không biết.
 *
 * <p>MVP cho người đăng ký tự chọn {@code role}, kể cả TEACHER (README 3.1). Khi cần duyệt tài
 * khoản giáo viên thì bỏ field này và để mặc định STUDENT.
 */
record RegisterRequest(
        @NotBlank(message = "Không được để trống")
        @Size(min = 3, max = UserAccount.USERNAME_MAX_LENGTH, message = "Phải dài từ 3 đến 50 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "Chỉ gồm chữ cái không dấu, chữ số và các ký tự . _ -")
        String username,

        @NotBlank(message = "Không được để trống")
        @Size(min = 8, max = 72, message = "Phải dài từ 8 đến 72 ký tự")
        String password,

        @NotBlank(message = "Không được để trống")
        @Size(max = UserAccount.FULL_NAME_MAX_LENGTH, message = "Tối đa 100 ký tự")
        String fullName,

        @NotNull(message = "Không được để trống")
        Role role) {
}
