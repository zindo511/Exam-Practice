package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.web.PageResponse;

/**
 * Ngân hàng câu hỏi của giáo viên: tạo, danh sách, xem, sửa, lưu trữ.
 *
 * <p>Mọi hàm nhận {@code teacherId} của người gọi (ADR-0015) và chỉ thao tác trên câu hỏi của
 * người đó. Câu của người khác hoặc đã lưu trữ đều báo {@code QUESTION_NOT_FOUND} (404).
 */
@Service
class QuestionService {

    /** Thứ tự mặc định khi client không gửi {@code sort}: mới nhất trước. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final QuestionRepository repository;
    private final ExamRepository examRepository;

    QuestionService(QuestionRepository repository, ExamRepository examRepository) {
        this.repository = repository;
        this.examRepository = examRepository;
    }

    @Transactional
    QuestionResponse create(Long teacherId, QuestionRequest request) {
        Question question = Question.create(teacherId, request.content(), request.options(),
                request.correctOption(), Instant.now());
        return QuestionResponse.from(repository.save(question));
    }

    /** @param keyword lọc theo nội dung; rỗng hoặc toàn khoảng trắng thì không lọc */
    @Transactional(readOnly = true)
    PageResponse<QuestionResponse> list(Long teacherId, String keyword, Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        var page = (keyword == null || keyword.isBlank())
                ? repository.findByTeacherIdAndArchivedAtIsNull(teacherId, sorted)
                : repository.findByTeacherIdAndArchivedAtIsNullAndContentContainingIgnoreCase(
                        teacherId, keyword.trim(), sorted);
        return PageResponse.from(page.map(QuestionResponse::from));
    }

    @Transactional(readOnly = true)
    QuestionResponse get(Long teacherId, Long questionId) {
        return QuestionResponse.from(findActiveOwned(teacherId, questionId));
    }

    /** Không cần gọi save: entity đang được theo dõi, Hibernate tự sinh UPDATE khi commit. */
    @Transactional
    QuestionResponse update(Long teacherId, Long questionId, QuestionRequest request) {
        Question question = findActiveOwned(teacherId, questionId);
        question.update(request.content(), request.options(), request.correctOption(), Instant.now());
        return QuestionResponse.from(question);
    }

    /**
     * Lưu trữ (xóa mềm). Câu đang nằm trong một đề chưa lưu trữ thì báo {@code QUESTION_IN_USE},
     * kèm tên các đề đó để giáo viên bỏ câu khỏi đề trước (state-machines.md mục 3). Đề đã lưu trữ
     * không tính, vì không còn dùng cho kỳ thi nào chưa bắt đầu.
     */
    @Transactional
    void archive(Long teacherId, Long questionId) {
        Question question = findActiveOwned(teacherId, questionId);
        List<String> examTitles = examRepository.findActiveExamTitlesContaining(questionId);
        if (!examTitles.isEmpty()) {
            throw new BusinessException(ExamDesignErrorCode.QUESTION_IN_USE,
                    "Câu hỏi đang nằm trong đề: " + String.join(", ", examTitles));
        }
        question.archive(Instant.now());
    }

    private Question findActiveOwned(Long teacherId, Long questionId) {
        return repository.findByIdAndTeacherIdAndArchivedAtIsNull(questionId, teacherId)
                .orElseThrow(() -> new BusinessException(ExamDesignErrorCode.QUESTION_NOT_FOUND));
    }
}
