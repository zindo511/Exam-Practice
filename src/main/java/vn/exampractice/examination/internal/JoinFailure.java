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
 * Một lần học sinh nhập sai mã kỳ thi (bảng {@code exam_join_failure}), dùng để tạm chặn khi sai quá
 * nhiều (ADR-0010, {@link JoinAttemptLimiter}). Chỉ ghi thêm, không sửa.
 */
@Entity
@Table(name = "exam_join_failure")
class JoinFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Column(name = "failed_at", nullable = false, updatable = false)
    private Instant failedAt;

    /** Chỉ dành cho JPA. */
    protected JoinFailure() {
    }

    JoinFailure(Long studentId, Instant failedAt) {
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.failedAt = Objects.requireNonNull(failedAt, "failedAt");
    }

    Long getStudentId() {
        return studentId;
    }

    Instant getFailedAt() {
        return failedAt;
    }
}
