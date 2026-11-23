package vn.exampractice.examination.internal;

import jakarta.validation.constraints.NotNull;

/** Body của {@code PUT /api/exam/sessions/{id}/result-visibility}. */
record ResultVisibilityRequest(
        @NotNull(message = "Không được để trống")
        ResultVisibility resultVisibility) {
}
