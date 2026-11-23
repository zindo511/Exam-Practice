package vn.exampractice.examination.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/** Danh sách học sinh đã tham gia kỳ thi. */
interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    /** Học sinh đã có tên trong kỳ thi chưa; dùng {@code UNIQUE (session_id, student_id)}. */
    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);
}
