package vn.exampractice.examination.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Danh sách học sinh đã tham gia kỳ thi. */
interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    /** Học sinh đã có tên trong kỳ thi chưa; dùng {@code UNIQUE (session_id, student_id)}. */
    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    /** Một trang học sinh đã tham gia kỳ thi, cho bảng điểm của giáo viên. */
    Page<SessionParticipant> findBySessionId(Long sessionId, Pageable pageable);

    /** Số học sinh đã tham gia kỳ thi, cho phần tóm tắt của bảng điểm. */
    long countBySessionId(Long sessionId);
}
