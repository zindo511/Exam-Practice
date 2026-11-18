package vn.exampractice.examination.internal;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn và lưu {@link ExamSession}. Truy vấn cho giáo viên luôn lọc theo {@code teacherId}; truy
 * vấn cho học sinh đi qua danh sách tham gia ({@link SessionParticipant}).
 */
interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    /** Kỳ thi của một giáo viên; không có thì rỗng (không phân biệt "không có" và "của người khác"). */
    Optional<ExamSession> findByIdAndTeacherId(Long id, Long teacherId);

    Page<ExamSession> findByTeacherId(Long teacherId, Pageable pageable);

    Page<ExamSession> findByTeacherIdAndStatus(Long teacherId, SessionStatus status, Pageable pageable);

    boolean existsByAccessCode(String accessCode);

    Optional<ExamSession> findByAccessCode(String accessCode);

    /**
     * Các kỳ thi một học sinh đã tham gia, lấy bằng một truy vấn (danh sách tăng dần theo thời gian
     * nên có phân trang, README 6.8).
     */
    @Query("""
            select s from ExamSession s
            where s.id in (select p.sessionId from SessionParticipant p where p.studentId = :studentId)""")
    Page<ExamSession> findJoinedBy(@Param("studentId") Long studentId, Pageable pageable);
}
