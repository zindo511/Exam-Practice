package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn và lưu {@link Attempt}. Đáp án ({@link Answer}) là thực thể con nên không có repository
 * riêng (README 6.8).
 */
interface AttemptRepository extends JpaRepository<Attempt, Long> {

    /**
     * Tải bài để ghi. {@code OPTIMISTIC_FORCE_INCREMENT} tăng {@code version} khi commit kể cả khi chỉ
     * sửa một {@link Answer} có sẵn, nên hai thao tác ghi đồng thời luôn có một bên bị từ chối
     * ({@code CONCURRENT_MODIFICATION}, ADR-0013).
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select a from Attempt a where a.id = :id")
    Optional<Attempt> findForUpdate(@Param("id") Long id);

    Optional<Attempt> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    /** Bài làm của một kỳ thi cho giáo viên xem, sắp theo lúc bắt đầu; tải luôn đáp án để tránh N+1. */
    @EntityGraph(attributePaths = "answers")
    List<Attempt> findBySessionIdOrderByStartedAtAscIdAsc(Long sessionId);

    /** Bài đang làm của một kỳ thi; dùng khi kỳ thi đóng. */
    List<Attempt> findBySessionIdAndStatus(Long sessionId, AttemptStatus status);

    /**
     * ID các bài đang làm có hạn chót trước {@code cutoff}, hạn sớm nhất trước. Cho job chốt bài hết
     * giờ (ADR-0013): job truyền {@code now − ân hạn} rồi chốt từng bài trong transaction riêng.
     */
    @Query("""
            select a.id from Attempt a
            where a.status = vn.exampractice.examination.internal.AttemptStatus.IN_PROGRESS
              and a.deadline < :cutoff
            order by a.deadline, a.id""")
    List<Long> findOverdueIds(@Param("cutoff") Instant cutoff, Limit limit);
}
