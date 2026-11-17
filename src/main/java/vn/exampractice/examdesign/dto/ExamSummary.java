package vn.exampractice.examdesign.dto;

/**
 * Thông tin tóm tắt của một đề, trả cho examination khi tạo kỳ thi: id, tiêu đề, thời lượng, số câu.
 *
 * @param durationMinutes thời lượng làm bài, tính bằng phút
 * @param questionCount   luôn lớn hơn 0, vì đề không có câu nào thì không dùng được
 */
public record ExamSummary(
        Long id,
        String title,
        int durationMinutes,
        int questionCount) {
}
