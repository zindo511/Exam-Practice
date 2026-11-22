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
 * Học sinh đã nhập đúng mã và vào phòng chờ của một kỳ thi (bảng {@code exam_participant},
 * ADR-0010). Không có vòng đời: có dòng là đã tham gia (state-machines.md mục 4).
 *
 * <p>Liên kết với kỳ thi và học sinh chỉ bằng ID (ADR-0007).
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

    SessionParticipant(Long sessionId, Long studentId, Instant now) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.joinedAt = Objects.requireNonNull(now, "now");
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
