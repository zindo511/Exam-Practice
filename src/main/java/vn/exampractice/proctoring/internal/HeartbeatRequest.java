package vn.exampractice.proctoring.internal;

import jakarta.validation.constraints.Min;

/**
 * Body của {@code POST /api/proctor/attempts/{attemptId}/heartbeat}. Không có ID học sinh: lấy từ token
 * (ADR-0015).
 *
 * @param currentQuestion vị trí câu đang xem, bắt đầu từ 1; rỗng khi không xem câu nào
 */
record HeartbeatRequest(
        @Min(value = 1, message = "Phải lớn hơn hoặc bằng 1")
        Integer currentQuestion) {
}
