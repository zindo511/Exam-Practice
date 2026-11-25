package vn.exampractice.examination.internal;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Danh sách học sinh đã tham gia kỳ thi. */
interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    /** Học sinh đã có tên trong kỳ thi chưa; dùng {@code UNIQUE (session_id, student_id)}. */
    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    /**
     * Một trang bảng điểm của giáo viên: học sinh đã tham gia, ghép với bài làm (nếu có) để lọc và xếp.
     *
     * <ul>
     *   <li>{@code notStarted = true}: chỉ học sinh chưa có bài làm.</li>
     *   <li>{@code status} khác rỗng: chỉ bài làm ở trạng thái đó.</li>
     *   <li>Thứ tự cố định: điểm cao trước; chưa có điểm (chưa làm, đang làm) xuống cuối; cùng điểm thì
     *       ai vào phòng chờ trước đứng trước. {@code Sort} trong {@code pageable} bị bỏ qua.</li>
     * </ul>
     */
    @Query(value = """
            select p from SessionParticipant p
            left join Attempt a on a.sessionId = p.sessionId and a.studentId = p.studentId
            where p.sessionId = :sessionId
              and (:notStarted = false or a.id is null)
              and (:status is null or a.status = :status)
            order by a.score desc nulls last, p.joinedAt asc, p.id asc""",
            countQuery = """
            select count(p) from SessionParticipant p
            left join Attempt a on a.sessionId = p.sessionId and a.studentId = p.studentId
            where p.sessionId = :sessionId
              and (:notStarted = false or a.id is null)
              and (:status is null or a.status = :status)""")
    Page<SessionParticipant> findResultPage(@Param("sessionId") Long sessionId,
                                            @Param("notStarted") boolean notStarted,
                                            @Param("status") AttemptStatus status,
                                            Pageable pageable);

    /** Mọi học sinh đã tham gia kỳ thi, ai vào phòng chờ trước đứng trước; cho màn hình giám sát. */
    List<SessionParticipant> findBySessionIdOrderByJoinedAtAscIdAsc(Long sessionId);

    /** Số học sinh đã tham gia kỳ thi, cho phần tóm tắt của bảng điểm. */
    long countBySessionId(Long sessionId);
}
