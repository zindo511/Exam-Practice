package vn.exampractice.proctoring.internal;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một dòng nhật ký giám sát (bảng {@code proctor_event}), làm bằng chứng cho giáo viên (ADR-0012). Chỉ
 * ghi thêm, không sửa.
 */
@Entity
@Table(name = "proctor_event")
class ProctorEvent {

    static final int MAX_DETAIL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private Long attemptId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private ProctorEventType type;

    @Column(name = "detail", updatable = false, length = MAX_DETAIL_LENGTH)
    private String detail;

    /** Giờ xảy ra; với sự kiện từ trình duyệt là giờ máy học sinh, chỉ để tham khảo. */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Giờ server nhận; mọi so sánh thời gian dùng cột này. */
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    /** Chỉ dành cho JPA. */
    protected ProctorEvent() {
    }

    private ProctorEvent(Long attemptId, Long sessionId, Long studentId, ProctorEventType type, String detail,
            Instant occurredAt, Instant receivedAt) {
        this.attemptId = attemptId;
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.type = type;
        this.detail = detail;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    /**
     * @param detail ghi chú thêm, có thể rỗng, tối đa {@value #MAX_DETAIL_LENGTH} ký tự
     */
    static ProctorEvent record(Long attemptId, Long sessionId, Long studentId, ProctorEventType type, String detail,
            Instant occurredAt, Instant receivedAt) {
        if (detail != null && detail.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("detail must not exceed " + MAX_DETAIL_LENGTH + " characters");
        }
        return new ProctorEvent(Objects.requireNonNull(attemptId, "attemptId"),
                Objects.requireNonNull(sessionId, "sessionId"), Objects.requireNonNull(studentId, "studentId"),
                Objects.requireNonNull(type, "type"), detail, Objects.requireNonNull(occurredAt, "occurredAt"),
                Objects.requireNonNull(receivedAt, "receivedAt"));
    }

    Long getId() {
        return id;
    }

    Long getAttemptId() {
        return attemptId;
    }

    Long getSessionId() {
        return sessionId;
    }

    Long getStudentId() {
        return studentId;
    }

    ProctorEventType getType() {
        return type;
    }

    String getDetail() {
        return detail;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    Instant getReceivedAt() {
        return receivedAt;
    }
}
