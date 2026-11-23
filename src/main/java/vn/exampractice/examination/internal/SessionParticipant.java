package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Học sinh đã nhập mã tham gia một kỳ thi (bảng {@code exam_participant}, ADR-0010).
 *
 * <p>Là dấu vết để học sinh quay lại phòng chờ mà không phải nhập lại mã, kể cả khi giáo viên đã khóa
 * tham gia; cũng là điều kiện "đã tham gia" khi bắt đầu làm bài.
 *
 * <p>Không phải danh sách con của {@link ExamSession}: số người tham gia lớn và tăng dần, nên lưu
 * {@code sessionId} ở phía con và truy vấn qua repository riêng (README 6.8). {@code studentId} là ID
 * của {@code identity_user}, chỉ lưu số (ADR-0007).
 */
@Entity
@Table(name = "exam_participant")
class SessionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    /** Chỉ dành cho JPA. */
    protected SessionParticipant() {
    }

    private SessionParticipant(Long sessionId, Long studentId, Instant joinedAt) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.joinedAt = joinedAt;
    }

    static SessionParticipant join(Long sessionId, Long studentId, Instant now) {
        return new SessionParticipant(Objects.requireNonNull(sessionId, "sessionId"),
                Objects.requireNonNull(studentId, "studentId"), Objects.requireNonNull(now, "now"));
    }

    Long getId() {
        return id;
    }

    Long getSessionId() {
        return sessionId;
    }

    Long getStudentId() {
        return studentId;
    }

    Instant getJoinedAt() {
        return joinedAt;
    }
}
