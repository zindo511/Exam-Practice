package vn.exampractice.examdesign.internal;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code POST} và {@code PUT /api/design/questions}. Sửa câu hỏi gửi lại đủ cả ba field,
 * vì {@code correctOption} phụ thuộc vào {@code options}.
 *
 * @param correctOption vị trí đáp án đúng trong {@code options}, bắt đầu từ 0
 */
record QuestionRequest(
        @NotBlank(message = "Không được để trống")
        @Size(max = 2000, message = "Tối đa 2000 ký tự")
        String content,

        @NotNull(message = "Không được để trống")
        @Size(min = Question.MIN_OPTIONS, max = Question.MAX_OPTIONS, message = "Phải có từ 2 đến 6 lựa chọn")
        List<@NotBlank(message = "Lựa chọn không được để trống")
             @Size(max = 500, message = "Mỗi lựa chọn tối đa 500 ký tự") String> options,

        @NotNull(message = "Không được để trống")
        @Min(value = 0, message = "Phải lớn hơn hoặc bằng 0")
        Integer correctOption) {

    /**
     * {@code correctOption} phải trỏ vào một lựa chọn có thật. Khi một trong hai field còn trống
     * thì bỏ qua, vì lỗi đã được báo ở field đó.
     */
    @AssertTrue(message = "Phải là vị trí của một lựa chọn trong options")
    boolean isCorrectOptionInRange() {
        return options == null || correctOption == null || correctOption < options.size();
    }
}
