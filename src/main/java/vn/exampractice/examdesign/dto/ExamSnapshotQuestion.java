package vn.exampractice.examdesign.dto;

import java.util.List;

/**
 * Một câu trong snapshot đề: nội dung, các lựa chọn, vị trí đáp án đúng. Mỗi câu 1 điểm (ADR-0016).
 *
 * <p>Có đáp án đúng để server chấm; examination không được gửi {@code correctOption} xuống trình
 * duyệt học sinh (ADR-0003).
 *
 * @param position         thứ tự câu trong đề, bắt đầu từ 1
 * @param sourceQuestionId ID câu hỏi gốc trong ngân hàng, để truy vết
 * @param correctOption    vị trí đáp án đúng trong {@code options}, bắt đầu từ 0
 */
public record ExamSnapshotQuestion(
        int position,
        Long sourceQuestionId,
        String content,
        List<String> options,
        int correctOption) {

    public ExamSnapshotQuestion {
        options = List.copyOf(options);
    }
}
