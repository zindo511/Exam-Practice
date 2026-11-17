package vn.exampractice.examdesign.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Truy vấn và lưu {@link Question}. Truy vấn tìm kiếm và phân trang theo giáo viên được thêm khi
 * viết QuestionService.
 */
interface QuestionRepository extends JpaRepository<Question, Long> {
}
