package vn.exampractice.examdesign.internal;

import java.net.URI;

import jakarta.validation.Valid;
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
 * Ngân hàng câu hỏi của giáo viên. Chỉ TEACHER gọi được (SecurityConfig, tiền tố
 * {@code /api/design/**}); quyền theo dữ liệu (chỉ câu của mình) do QuestionService kiểm tra.
 */
@RestController
@RequestMapping("/api/design/questions")
class QuestionController {

    private final QuestionService questionService;

    QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /** 201 kèm header {@code Location} trỏ tới câu vừa tạo (README 6.4). */
    @PostMapping
    ResponseEntity<QuestionResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody QuestionRequest request) {
        QuestionResponse created = questionService.create(user.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Mặc định mới nhất trước; {@code ?keyword=} lọc theo nội dung. */
    @GetMapping
    PageResponse<QuestionResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestParam(required = false) String keyword,
                                        Pageable pageable) {
        return questionService.list(user.id(), keyword, pageable);
    }

    @GetMapping("/{id}")
    QuestionResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return questionService.get(user.id(), id);
    }

    @PutMapping("/{id}")
    QuestionResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                            @Valid @RequestBody QuestionRequest request) {
        return questionService.update(user.id(), id, request);
    }

    /** Lưu trữ (xóa mềm), không xóa hẳn dòng trong database. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        questionService.archive(user.id(), id);
    }
}
