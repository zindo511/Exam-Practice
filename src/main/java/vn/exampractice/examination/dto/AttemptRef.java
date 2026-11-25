package vn.exampractice.examination.dto;

/**
 * Bài làm thuộc học sinh nào, kỳ thi nào; proctoring dùng để kiểm tra và ghi sự kiện giám sát (ADR-0019).
 */
public record AttemptRef(Long attemptId, Long sessionId, Long studentId, AttemptState status) {
}
