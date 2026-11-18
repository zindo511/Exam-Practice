package vn.exampractice.examination.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /api/exam/sessions/join}. Mã được bỏ khoảng trắng hai đầu và đổi sang chữ hoa
 * trước khi so, nên học sinh gõ {@code "k7q2zp "} vẫn đúng.
 */
record JoinRequest(
        @NotBlank(message = "Không được để trống")
        @Size(max = 20, message = "Tối đa 20 ký tự")
        String accessCode) {
}
