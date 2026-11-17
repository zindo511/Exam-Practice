package vn.exampractice.examdesign.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST /api/design/exams} và {@code PUT /api/design/exams/{id}}: tiêu đề và thời
 * lượng. Danh sách câu thay qua {@code PUT /api/design/exams/{id}/questions}.
 *
 * @param durationMinutes thời lượng làm bài, tính bằng phút
 */
record ExamRequest(
        @NotBlank(message = "Không được để trống")
        @Size(max = Exam.MAX_TITLE_LENGTH, message = "Tối đa 200 ký tự")
        String title,

        @NotNull(message = "Không được để trống")
        @Min(value = 1, message = "Ít nhất 1 phút")
        @Max(value = Exam.MAX_DURATION_MINUTES, message = "Tối đa 300 phút")
        Integer durationMinutes) {
}
