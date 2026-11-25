package vn.exampractice.proctoring.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body của {@code POST /api/proctor/attempts/{attemptId}/extend-time}.
 *
 * @param minutes số phút cộng thêm; không có mức tối đa (ADR-0018)
 */
record ExtendTimeRequest(
        @NotNull(message = "Không được để trống")
        @Min(value = 1, message = "Ít nhất 1 phút")
        Integer minutes) {
}
