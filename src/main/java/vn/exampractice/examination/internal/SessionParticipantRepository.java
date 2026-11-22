package vn.exampractice.examination.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn và lưu {@link SessionParticipant}. */
interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    Optional<SessionParticipant> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);
}
