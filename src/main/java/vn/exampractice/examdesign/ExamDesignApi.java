package vn.exampractice.examdesign;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import vn.exampractice.examdesign.dto.ExamSnapshot;
import vn.exampractice.examdesign.dto.ExamSummary;

/**
 * Cổng gọi đồng bộ cho module examination (ADR-0004): lấy thông tin đề khi tạo kỳ thi và chụp
 * snapshot đề khi mở kỳ thi (ADR-0003).
 *
 * <p><b>Đề dùng được</b> nghĩa là: tồn tại, thuộc giáo viên {@code teacherId}, chưa lưu trữ và có ít
 * nhất 1 câu (state-machines.md mục 1 và 3). Mọi hàm dùng chung luật này; đề không dùng được
 * thì trả {@link Optional#empty()}, không phân biệt lý do, để không lộ đề nào đang tồn tại.
 *
 * <p>Hàm trả {@code Optional} thay vì ném lỗi: mã lỗi {@code EXAM_NOT_AVAILABLE} thuộc về
 * examination, nên examination tự quyết định báo lỗi gì khi nhận về rỗng.
 */
public interface ExamDesignApi {

    /** Thông tin tóm tắt của đề, dùng khi giáo viên tạo kỳ thi. */
    Optional<ExamSummary> getExamSummary(Long examId, Long teacherId);

    /**
     * Toàn bộ nội dung đề tại thời điểm gọi, kèm đáp án đúng, dùng khi mở kỳ thi. Examination lưu
     * bản sao vào bảng của mình; sau đó sửa đề không ảnh hưởng tới kỳ thi (ADR-0003).
     */
    Optional<ExamSnapshot> getExamSnapshot(Long examId, Long teacherId);

    /**
     * Trong các đề {@code examIds}, những đề còn dùng được, bằng một truy vấn. Examination dùng để
     * đánh dấu kỳ thi đã lên lịch mà đề không còn mở được nữa (state-machines.md mục 1).
     *
     * @return tập con của {@code examIds}; đề không dùng được hoặc của giáo viên khác không có trong đó
     */
    Set<Long> findUsableExamIds(Collection<Long> examIds, Long teacherId);
}
