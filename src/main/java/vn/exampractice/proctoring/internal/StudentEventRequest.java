package vn.exampractice.proctoring.internal;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/**
 * Body của {@code POST /api/proctor/attempts/{attemptId}/events}. Không có ID học sinh: lấy từ token
 * (ADR-0015).
 *
 * @param type       thao tác trình duyệt bắt được
 * @param occurredAt giờ máy học sinh lúc xảy ra, chỉ để tham khảo
 */
record StudentEventRequest(
        @NotNull(message = "Không được để trống")
        BrowserEventType type,

        @NotNull(message = "Không được để trống")
        Instant occurredAt) {
}
