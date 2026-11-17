package vn.exampractice.examdesign.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn và lưu {@link Exam}. Mọi truy vấn cho giáo viên đều lọc theo {@code teacherId} và chỉ
 * lấy đề chưa lưu trữ; dùng được partial index {@code ix_design_exam_teacher_active}.
 */
interface ExamRepository extends JpaRepository<Exam, Long> {

    /** Số câu của một đề, dùng cho danh sách đề mà không phải tải danh sách câu của từng đề. */
    interface QuestionCount {
        Long getExamId();

        int getCount();
    }

    /** Đề đang dùng của một giáo viên; không có thì rỗng (không phân biệt "không có" và "của người khác"). */
    Optional<Exam> findByIdAndTeacherIdAndArchivedAtIsNull(Long id, Long teacherId);

    /** Danh sách đề của giáo viên, không lọc tiêu đề. */
    Page<Exam> findByTeacherIdAndArchivedAtIsNull(Long teacherId, Pageable pageable);

    /** Danh sách đề có tiêu đề chứa {@code keyword}, không phân biệt hoa thường. */
    Page<Exam> findByTeacherIdAndArchivedAtIsNullAndTitleContainingIgnoreCase(
            Long teacherId, String keyword, Pageable pageable);

    /**
     * Số câu của nhiều đề trong một truy vấn (README 6.8: gom ID rồi lấy bằng {@code IN}), tránh
     * N+1 khi hiển thị một trang danh sách đề.
     */
    @Query("select e.id as examId, size(e.questions) as count from Exam e where e.id in :examIds")
    List<QuestionCount> countQuestions(@Param("examIds") Collection<Long> examIds);

    /**
     * Tiêu đề các đề chưa lưu trữ đang chứa câu hỏi này, theo thứ tự chữ cái; dùng index
     * {@code ix_design_exam_question_question}. Rỗng nghĩa là câu không nằm trong đề nào đang dùng.
     */
    @Query("""
            select e.title from Exam e join e.questions q
            where q.questionId = :questionId and e.archivedAt is null
            order by e.title""")
    List<String> findActiveExamTitlesContaining(@Param("questionId") Long questionId);
}
