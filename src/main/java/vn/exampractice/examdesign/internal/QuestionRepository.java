package vn.exampractice.examdesign.internal;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Truy vấn và lưu {@link Question}. Mọi truy vấn cho giáo viên đều lọc theo {@code teacherId} và
 * chỉ lấy câu chưa lưu trữ; dùng được partial index {@code ix_design_question_teacher_active}.
 */
interface QuestionRepository extends JpaRepository<Question, Long> {

    /** Câu đang dùng của một giáo viên; không có thì rỗng (không phân biệt "không có" và "của người khác"). */
    Optional<Question> findByIdAndTeacherIdAndArchivedAtIsNull(Long id, Long teacherId);

    /** Ngân hàng câu hỏi của giáo viên, không lọc nội dung. */
    Page<Question> findByTeacherIdAndArchivedAtIsNull(Long teacherId, Pageable pageable);

    /**
     * Ngân hàng câu hỏi có nội dung chứa {@code keyword}, không phân biệt hoa thường. Spring Data tự
     * thoát ký tự {@code %} và {@code _} trong keyword.
     */
    Page<Question> findByTeacherIdAndArchivedAtIsNullAndContentContainingIgnoreCase(
            Long teacherId, String keyword, Pageable pageable);
}
