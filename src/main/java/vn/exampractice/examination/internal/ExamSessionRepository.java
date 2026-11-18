package vn.exampractice.examination.internal;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Truy vấn và lưu {@link ExamSession}. Truy vấn cho giáo viên luôn lọc theo {@code teacherId}.
 */
interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    /** Kỳ thi của một giáo viên; không có thì rỗng (không phân biệt "không có" và "của người khác"). */
    Optional<ExamSession> findByIdAndTeacherId(Long id, Long teacherId);

    Page<ExamSession> findByTeacherId(Long teacherId, Pageable pageable);

    Page<ExamSession> findByTeacherIdAndStatus(Long teacherId, SessionStatus status, Pageable pageable);

    boolean existsByAccessCode(String accessCode);
}
