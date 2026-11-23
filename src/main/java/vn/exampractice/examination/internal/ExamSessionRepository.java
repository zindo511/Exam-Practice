package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
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

    /**
     * Kỳ thi job cần mở: đã tới giờ mở, chưa tới giờ đóng. Chỉ trả ID; mỗi kỳ thi được xử lý trong
     * transaction riêng (ADR-0013). Dùng partial index {@code ix_exam_session_scheduled_start}.
     */
    @Query("""
            select s.id from ExamSession s
            where s.status = vn.exampractice.examination.internal.SessionStatus.SCHEDULED
              and s.startAt <= :now and s.endAt > :now
            order by s.startAt, s.id""")
    List<Long> findIdsDueToOpen(@Param("now") Instant now, Limit limit);

    /**
     * Kỳ thi job cần đóng (đang mở) hoặc hủy (chưa từng mở), có giờ đóng trước {@code cutoff}. Dùng partial
     * index {@code ix_exam_session_active_end}.
     */
    @Query("""
            select s.id from ExamSession s
            where s.status in (vn.exampractice.examination.internal.SessionStatus.SCHEDULED,
                               vn.exampractice.examination.internal.SessionStatus.OPEN)
              and s.endAt < :cutoff
            order by s.endAt, s.id""")
    List<Long> findIdsDueToClose(@Param("cutoff") Instant cutoff, Limit limit);
}
