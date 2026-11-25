package vn.exampractice.proctoring.internal;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Nhật ký giám sát. */
interface ProctorEventRepository extends JpaRepository<ProctorEvent, Long> {

    /** Nhật ký của một bài làm, mới nhất trước. */
    Page<ProctorEvent> findByAttemptIdOrderByReceivedAtDescIdDesc(Long attemptId, Pageable pageable);

    /** Số sự kiện thuộc {@code types} của từng bài trong kỳ thi; bài không có sự kiện nào thì không có dòng. */
    @Query("""
            select e.attemptId as attemptId, count(e) as total
            from ProctorEvent e
            where e.sessionId = :sessionId and e.type in :types
            group by e.attemptId
            """)
    List<EventCount> countBySession(@Param("sessionId") Long sessionId,
                                    @Param("types") Collection<ProctorEventType> types);

    /** Một dòng kết quả của {@link #countBySession}. */
    interface EventCount {

        Long getAttemptId();

        long getTotal();
    }
}
