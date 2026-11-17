package vn.exampractice.examdesign.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Truy vấn đề của một giáo viên: tìm theo tiêu đề, phân trang, chỉ đề chưa lưu trữ.
 * Các truy vấn được thêm khi viết ExamService.
 */
interface ExamRepository extends JpaRepository<Exam, Long> {
}
