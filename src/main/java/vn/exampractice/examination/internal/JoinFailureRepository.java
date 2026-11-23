package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Các lần nhập sai mã kỳ thi. */
interface JoinFailureRepository extends JpaRepository<JoinFailure, Long> {

    /**
     * Tối đa 5 lần sai gần nhất của học sinh từ {@code since} tới giờ, mới nhất trước; đủ để
     * {@link JoinAttemptLimiter} quyết định có chặn hay không. Dùng index
     * {@code ix_exam_join_failure_student}.
     */
    List<JoinFailure> findTop5ByStudentIdAndFailedAtAfterOrderByFailedAtDesc(Long studentId, Instant since);
}
