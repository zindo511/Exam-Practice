package vn.exampractice.examdesign.dto;

import java.util.List;

/**
 * Toàn bộ nội dung đề tại thời điểm mở kỳ thi: tiêu đề, thời lượng và danh sách câu theo thứ tự
 * (ADR-0003). Mỗi câu 1 điểm (ADR-0016).
 *
 * @param questions theo thứ tự trong đề, không rỗng, không sửa được
 */
public record ExamSnapshot(
        Long examId,
        String title,
        int durationMinutes,
        List<ExamSnapshotQuestion> questions) {

    public ExamSnapshot {
        questions = List.copyOf(questions);
    }
}
