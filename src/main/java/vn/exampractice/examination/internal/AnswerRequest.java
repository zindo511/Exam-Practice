package vn.exampractice.examination.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body của {@code PUT /api/exam/attempts/{id}/answers/{position}}.
 *
 * @param selectedOption vị trí lựa chọn, bắt đầu từ 0
 */
record AnswerRequest(
        @NotNull(message = "Không được để trống")
        @Min(value = 0, message = "Phải lớn hơn hoặc bằng 0")
        Integer selectedOption) {
}
