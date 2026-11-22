package vn.exampractice.examination.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /api/exam/participations}. Mã không phân biệt hoa thường và được bỏ khoảng
 * trắng hai đầu trước khi tra (ADR-0010).
 */
record JoinRequest(
        @NotBlank(message = "Không được để trống")
        @Size(max = 20, message = "Tối đa 20 ký tự")
        String accessCode) {
}
