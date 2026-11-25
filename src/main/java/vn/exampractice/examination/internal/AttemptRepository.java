package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
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

    /**
     * Bài làm để giáo viên ghi (cộng giờ, khóa bài), qua {@code ExaminationApi}. Không lọc theo học sinh;
     * quyền của giáo viên kiểm tra qua kỳ thi. Cùng khóa {@code OPTIMISTIC_FORCE_INCREMENT} như
     * {@link #findForWrite}.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select a from Attempt a where a.id = :id")
    Optional<Attempt> findForTeacherWrite(@Param("id") Long id);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    Optional<Attempt> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    /** Bài làm của kỳ thi theo ID; bài của kỳ thi khác thì rỗng. Dùng cho giáo viên xem chi tiết. */
    Optional<Attempt> findByIdAndSessionId(Long id, Long sessionId);

    /** Mọi bài làm của kỳ thi, để tính phần tóm tắt của bảng điểm. */
    List<Attempt> findBySessionId(Long sessionId);

    /** Bài làm của nhiều học sinh trong một kỳ thi, cho một trang bảng điểm (một truy vấn). */
    List<Attempt> findBySessionIdAndStudentIdIn(Long sessionId, Collection<Long> studentIds);

    /** Các bài còn đang làm của kỳ thi, để chốt khi kỳ thi đóng. */
    List<Attempt> findBySessionIdAndStatus(Long sessionId, AttemptStatus status);

    /**
     * Kỳ thi còn bài đang làm mà chưa hết ân hạn không: hạn chót từ {@code cutoff} trở đi
     * ({@code cutoff = now − ân hạn}). Bài được cộng giờ có thể có hạn chót sau giờ đóng kỳ thi (ADR-0018).
     */
    boolean existsBySessionIdAndStatusAndDeadlineGreaterThanEqual(Long sessionId, AttemptStatus status, Instant cutoff);

    /** Hạn chót muộn nhất trong các bài làm của kỳ thi; rỗng nếu chưa có bài nào. */
    @Query("select max(a.deadline) from Attempt a where a.sessionId = :sessionId")
    Optional<Instant> findLatestDeadline(@Param("sessionId") Long sessionId);

    /** Số câu đã trả lời của từng bài làm trong kỳ thi, đếm bằng một truy vấn, không tải đáp án. */
    @Query("""
            select a.id as attemptId, count(ans.id) as answered
            from Attempt a left join a.answers ans
            where a.sessionId = :sessionId
            group by a.id""")
    List<AnsweredCount> countAnswersBySession(@Param("sessionId") Long sessionId);

    /** Kết quả của {@link #countAnswersBySession}. */
    interface AnsweredCount {

        Long getAttemptId();

        long getAnswered();
    }

    /** Bài làm của một học sinh trong nhiều kỳ thi, cho một trang "Kỳ thi của tôi" (một truy vấn). */
    List<Attempt> findByStudentIdAndSessionIdIn(Long studentId, Collection<Long> sessionIds);

    /**
     * Bài job cần chốt: còn đang làm, hạn chót trước {@code cutoff}. Chỉ trả ID; mỗi bài được chốt
     * trong transaction riêng (ADR-0013). Dùng partial index {@code ix_exam_attempt_in_progress_deadline}.
     */
    @Query("""
            select a.id from Attempt a
            where a.status = vn.exampractice.examination.internal.AttemptStatus.IN_PROGRESS
              and a.deadline < :cutoff
            order by a.deadline, a.id""")
    List<Long> findIdsOverdue(@Param("cutoff") Instant cutoff, Limit limit);
}
