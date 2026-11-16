package vn.exampractice.identity.internal;

import jakarta.validation.constraints.NotBlank;

/**
 * Body của {@code POST /api/auth/login}. Chỉ kiểm tra không trống; sai định dạng cũng chỉ là
 * "sai thông tin đăng nhập".
 */
record LoginRequest(
        @NotBlank(message = "Không được để trống") String username,
        @NotBlank(message = "Không được để trống") String password) {
}
