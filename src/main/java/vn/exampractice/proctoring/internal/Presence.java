package vn.exampractice.proctoring.internal;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Tín hiệu gần nhất từ trình duyệt của học sinh trong một bài làm (bảng {@code proctor_presence},
 * ADR-0011). Mỗi bài làm một dòng, tạo ở heartbeat đầu tiên.
 *
 * <p>Không chứa trạng thái bài làm: cái đó luôn hỏi {@code ExaminationApi}. Các ID trỏ sang module khác
 * nên chỉ lưu số (ADR-0007).
 *
 * <p>{@link DynamicUpdate}: heartbeat chỉ ghi các cột nó đổi, để không ghi đè {@code violation_count}
 * vừa được một request khác của cùng trình duyệt tăng lên.
 */
@Entity
@Table(name = "proctor_presence")
@DynamicUpdate
class Presence {

    /** ID bài làm của examination, cũng là khóa chính; không tự sinh. */
    @Id
    @Column(name = "attempt_id")
    private Long attemptId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** Vị trí câu đang xem, bắt đầu từ 1; rỗng khi trình duyệt không báo. */
    @Column(name = "current_question")
    private Integer currentQuestion;

    @Column(name = "violation_count", nullable = false)
    private int violationCount;

    /** Chỉ dành cho JPA. */
    protected Presence() {
    }

    private Presence(Long attemptId, Long sessionId, Long studentId, Instant now) {
        this.attemptId = attemptId;
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.firstSeenAt = now;
        this.lastSeenAt = now;
    }

    /** Heartbeat đầu tiên của một bài làm. Bên gọi đã xác nhận bài làm thuộc về học sinh này. */
    static Presence firstSeen(Long attemptId, Long sessionId, Long studentId, Integer currentQuestion, Instant now) {
        Presence presence = new Presence(Objects.requireNonNull(attemptId, "attemptId"),
                Objects.requireNonNull(sessionId, "sessionId"), Objects.requireNonNull(studentId, "studentId"),
                Objects.requireNonNull(now, "now"));
        presence.seen(currentQuestion, now);
        return presence;
    }

    /**
     * Nhận một heartbeat nữa. Heartbeat được xử lý trễ (hai request chạy chồng nhau) không kéo
     * {@code lastSeenAt} lùi lại và không ghi đè câu đang xem mới hơn.
     */
    void seen(Integer currentQuestion, Instant now) {
        if (currentQuestion != null && currentQuestion < 1) {
            throw new IllegalArgumentException("currentQuestion must be at least 1");
        }
        if (now.isBefore(lastSeenAt)) {
            return;
        }
        this.lastSeenAt = now;
        this.currentQuestion = currentQuestion;
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

    Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    Instant getLastSeenAt() {
        return lastSeenAt;
    }

    Integer getCurrentQuestion() {
        return currentQuestion;
    }

    int getViolationCount() {
        return violationCount;
    }
}
