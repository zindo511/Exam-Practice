package vn.exampractice.examdesign.internal;

import java.net.URI;

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import vn.exampractice.shared.security.AuthenticatedUser;
import vn.exampractice.shared.web.PageResponse;

/**
 * Đề thi của giáo viên. Chỉ TEACHER gọi được (SecurityConfig, tiền tố {@code /api/design/**});
 * quyền theo dữ liệu (chỉ đề của mình) do ExamService kiểm tra.
 */
@RestController
@RequestMapping("/api/design/exams")
class ExamController {

    private final ExamService examService;

    ExamController(ExamService examService) {
        this.examService = examService;
    }

    /**
     * 201 kèm header {@code Location} trỏ tới chi tiết đề vừa tạo (README 6.4). Đề mới chưa có câu
     * nào nên body chỉ là thông tin tóm tắt.
     */
    @PostMapping
    ResponseEntity<ExamResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody ExamRequest request) {
        ExamResponse created = examService.create(user.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Mặc định mới nhất trước; {@code ?keyword=} lọc theo tiêu đề. */
    @GetMapping
    PageResponse<ExamResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                    @RequestParam(required = false) String keyword,
                                    @ParameterObject Pageable pageable) {
        return examService.list(user.id(), keyword, pageable);
    }

    @GetMapping("/{id}")
    ExamDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return examService.get(user.id(), id);
    }

    /** Sửa tiêu đề và thời lượng; trả thông tin tóm tắt vì danh sách câu không đổi. */
    @PutMapping("/{id}")
    ExamResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                        @Valid @RequestBody ExamRequest request) {
        return examService.update(user.id(), id, request);
    }

    /** Thay toàn bộ danh sách câu; thứ tự trong mảng là thứ tự câu trong đề. */
    @PutMapping("/{id}/questions")
    ExamDetailResponse replaceQuestions(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                        @Valid @RequestBody ExamQuestionsRequest request) {
        return examService.replaceQuestions(user.id(), id, request);
    }

    /** Lưu trữ (xóa mềm), không xóa hẳn dòng trong database. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        examService.archive(user.id(), id);
    }
}
