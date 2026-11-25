package vn.exampractice.examination.dto;

/**
 * Bài làm thuộc học sinh nào, kỳ thi nào; proctoring lưu lại để kiểm tra các heartbeat sau tại chỗ
 * (ADR-0011).
 */
public record AttemptRef(Long attemptId, Long sessionId, Long studentId, AttemptState status) {
}
