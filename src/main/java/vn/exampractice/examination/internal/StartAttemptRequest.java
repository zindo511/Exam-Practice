package vn.exampractice.examination.internal;

import jakarta.validation.constraints.NotNull;

/** Body của {@code POST /api/exam/attempts}: kỳ thi muốn bắt đầu làm bài. */
record StartAttemptRequest(
        @NotNull(message = "Không được để trống")
        Long sessionId) {
}
