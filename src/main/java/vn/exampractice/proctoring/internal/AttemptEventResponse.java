package vn.exampractice.proctoring.internal;

import java.time.Instant;

/**
 * Một dòng nhật ký của một bài làm.
 *
 * @param detail     ghi chú, ví dụ "Cộng 10 phút"; có thể rỗng
 * @param occurredAt với sự kiện từ trình duyệt là giờ máy học sinh, chỉ để tham khảo
 * @param receivedAt giờ server nhận; giao diện hiển thị giờ này
 * @param violation  có tính vào số vi phạm trên bảng giám sát không
 */
record AttemptEventResponse(
        Long id,
        ProctorEventType type,
        String detail,
        Instant occurredAt,
        Instant receivedAt,
        boolean violation) {

    static AttemptEventResponse from(ProctorEvent event) {
        return new AttemptEventResponse(event.getId(), event.getType(), event.getDetail(), event.getOccurredAt(),
                event.getReceivedAt(), event.getType().isViolation());
    }
}
