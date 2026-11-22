package vn.exampractice.examination.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn và lưu {@link Attempt}. Truy vấn cho học sinh luôn lọc theo {@code studentId}. */
interface AttemptRepository extends JpaRepository<Attempt, Long> {

    /** Bài làm của học sinh để đọc; của người khác thì rỗng. */
    Optional<Attempt> findByIdAndStudentId(Long id, Long studentId);

    /**
     * Bài làm của học sinh để ghi. Khóa {@code OPTIMISTIC_FORCE_INCREMENT}: version luôn tăng lúc commit,
     * kể cả khi chỉ sửa một {@link Answer} đã có, nên thao tác ghi song song luôn bị phát hiện (ADR-0017).
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select a from Attempt a where a.id = :id and a.studentId = :studentId")
    Optional<Attempt> findForWrite(@Param("id") Long id, @Param("studentId") Long studentId);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    Optional<Attempt> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    /** Các bài còn đang làm của kỳ thi, để chốt khi kỳ thi đóng. */
    List<Attempt> findBySessionIdAndStatus(Long sessionId, AttemptStatus status);

    /** Bài làm của một học sinh trong nhiều kỳ thi, cho một trang "Kỳ thi của tôi" (một truy vấn). */
    List<Attempt> findByStudentIdAndSessionIdIn(Long studentId, Collection<Long> sessionIds);
}
