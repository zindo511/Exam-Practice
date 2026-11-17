package vn.exampractice.examdesign.internal;

import java.time.Instant;

/**
 * Một đề trong danh sách: tiêu đề, thời lượng, số câu. Mỗi câu 1 điểm nên số câu cũng là điểm tối
 * đa trước khi quy về thang 10 (ADR-0016).
 */
record ExamResponse(
        Long id,
        String title,
        int durationMinutes,
        int questionCount,
        Instant createdAt,
        Instant updatedAt) {

    /** @param questionCount lấy từ truy vấn đếm, để không phải tải danh sách câu của từng đề */
    static ExamResponse from(Exam exam, int questionCount) {
        return new ExamResponse(exam.getId(), exam.getTitle(), exam.getDurationMinutes(), questionCount,
                exam.getCreatedAt(), exam.getUpdatedAt());
    }
}
