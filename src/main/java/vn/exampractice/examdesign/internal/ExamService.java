package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.web.PageResponse;

/**
 * Đề thi của giáo viên: tạo, danh sách, xem, sửa thông tin, thay danh sách câu, lưu trữ.
 *
 * <p>Mọi hàm nhận {@code teacherId} của người gọi (ADR-0015) và chỉ thao tác trên đề của người đó.
 * Đề của người khác hoặc đã lưu trữ đều báo {@code EXAM_NOT_FOUND} (404).
 */
@Service
class ExamService {

    /** Thứ tự mặc định khi client không gửi {@code sort}: mới nhất trước. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;

    ExamService(ExamRepository examRepository, QuestionRepository questionRepository) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * Đề mới chưa có câu nào nên chỉ trả thông tin tóm tắt; thêm câu bằng {@link #replaceQuestions}.
     */
    @Transactional
    ExamResponse create(Long teacherId, ExamRequest request) {
        Exam exam = Exam.create(teacherId, request.title(), request.durationMinutes(), Instant.now());
        return ExamResponse.from(examRepository.save(exam), 0);
    }

    /**
     * Một trang đề, kèm số câu của từng đề. Số câu lấy bằng một truy vấn đếm cho cả trang, không tải
     * danh sách câu của từng đề.
     *
     * @param keyword lọc theo tiêu đề; rỗng hoặc toàn khoảng trắng thì không lọc
     */
    @Transactional(readOnly = true)
    PageResponse<ExamResponse> list(Long teacherId, String keyword, Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        Page<Exam> page = (keyword == null || keyword.isBlank())
                ? examRepository.findByTeacherIdAndArchivedAtIsNull(teacherId, sorted)
                : examRepository.findByTeacherIdAndArchivedAtIsNullAndTitleContainingIgnoreCase(
                        teacherId, keyword.trim(), sorted);
        Map<Long, Integer> counts = countQuestions(page.getContent());
        return PageResponse.from(page.map(exam -> ExamResponse.from(exam, counts.getOrDefault(exam.getId(), 0))));
    }

    @Transactional(readOnly = true)
    ExamDetailResponse get(Long teacherId, Long examId) {
        return toDetail(findActiveOwned(teacherId, examId));
    }

    /** Sửa tiêu đề và thời lượng; danh sách câu giữ nguyên nên chỉ trả thông tin tóm tắt. */
    @Transactional
    ExamResponse update(Long teacherId, Long examId, ExamRequest request) {
        Exam exam = findActiveOwned(teacherId, examId);
        exam.update(request.title(), request.durationMinutes(), Instant.now());
        return ExamResponse.from(exam, exam.questionCount());
    }

    /**
     * Thay toàn bộ danh sách câu. Mọi câu phải là câu đang dùng của chính giáo viên này, nếu không
     * báo {@code QUESTION_NOT_AVAILABLE} kèm ID các câu không dùng được. Câu trùng và số câu tối đa
     * đã được kiểm tra ở request, entity kiểm tra lại.
     */
    @Transactional
    ExamDetailResponse replaceQuestions(Long teacherId, Long examId, ExamQuestionsRequest request) {
        Exam exam = findActiveOwned(teacherId, examId);
        List<Long> questionIds = request.questionIds();
        requireAvailable(teacherId, questionIds);
        exam.replaceQuestions(questionIds, Instant.now());
        return toDetail(exam);
    }

    /** Lưu trữ (xóa mềm). Kỳ thi đã lên lịch từ đề này sẽ không mở được (state-machines.md mục 3). */
    @Transactional
    void archive(Long teacherId, Long examId) {
        findActiveOwned(teacherId, examId).archive(Instant.now());
    }

    private Exam findActiveOwned(Long teacherId, Long examId) {
        return examRepository.findByIdAndTeacherIdAndArchivedAtIsNull(examId, teacherId)
                .orElseThrow(() -> new BusinessException(ExamDesignErrorCode.EXAM_NOT_FOUND));
    }

    private void requireAvailable(Long teacherId, List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return;
        }
        Set<Long> available = new HashSet<>(questionRepository.findActiveIdsOwnedBy(teacherId, questionIds));
        List<Long> unavailable = questionIds.stream().filter(id -> !available.contains(id)).toList();
        if (!unavailable.isEmpty()) {
            throw new BusinessException(ExamDesignErrorCode.QUESTION_NOT_AVAILABLE,
                    "Câu hỏi không tồn tại hoặc không dùng được: " + unavailable);
        }
    }

    /** Lấy nội dung mọi câu của đề bằng một truy vấn, rồi ghép theo thứ tự trong đề. */
    private ExamDetailResponse toDetail(Exam exam) {
        List<Long> ids = exam.getQuestions().stream().map(ExamQuestion::getQuestionId).toList();
        Map<Long, Question> questionsById = ids.isEmpty()
                ? Map.of()
                : questionRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(Question::getId, Function.identity()));
        return ExamDetailResponse.from(exam, questionsById);
    }

    private Map<Long, Integer> countQuestions(List<Exam> exams) {
        if (exams.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = exams.stream().map(Exam::getId).toList();
        return examRepository.countQuestions(ids).stream()
                .collect(Collectors.toMap(ExamRepository.QuestionCount::getExamId,
                        ExamRepository.QuestionCount::getCount));
    }
}
