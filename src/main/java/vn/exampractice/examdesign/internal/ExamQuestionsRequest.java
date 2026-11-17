package vn.exampractice.examdesign.internal;

import java.util.HashSet;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body của {@code PUT /api/design/exams/{id}/questions}: toàn bộ danh sách câu mới của đề. Thứ tự
 * trong mảng là thứ tự câu; mảng rỗng là bỏ hết câu.
 */
record ExamQuestionsRequest(
        @NotNull(message = "Không được để trống")
        @Size(max = Exam.MAX_QUESTIONS, message = "Tối đa 100 câu")
        List<@NotNull(message = "ID câu hỏi không được để trống") Long> questionIds) {

    /** Một câu không xuất hiện hai lần trong đề. Danh sách trống hoặc có phần tử rỗng thì để lỗi khác báo. */
    @AssertTrue(message = "Một câu hỏi không được xuất hiện hai lần trong đề")
    boolean isWithoutDuplicates() {
        return questionIds == null || questionIds.contains(null)
                || new HashSet<>(questionIds).size() == questionIds.size();
    }
}
