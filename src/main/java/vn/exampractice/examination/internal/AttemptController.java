package vn.exampractice.examination.internal;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import vn.exampractice.shared.security.AuthenticatedUser;

/**
 * Học sinh làm bài. Quyền theo dữ liệu (chỉ bài của mình) do {@link AttemptService} kiểm tra.
 * Câu hỏi định danh bằng thứ tự trong đề ({@code position}, từ 1).
 */
@RestController
@RequestMapping("/api/exam/attempts")
@PreAuthorize("hasRole('STUDENT')")
class AttemptController {

    private final AttemptService attemptService;

    AttemptController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    /** 201 kèm {@code Location}; response có đề (không có đáp án đúng) và hạn chót. */
    @PostMapping
    ResponseEntity<AttemptResponse> start(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody StartAttemptRequest request) {
        AttemptResponse started = attemptService.start(user.id(), request.sessionId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(started.id()).toUri();
        return ResponseEntity.created(location).body(started);
    }

    @GetMapping("/{id}")
    AttemptResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return attemptService.get(user.id(), id);
    }

    /** Tự động lưu sau mỗi lần chọn; gửi lại cùng đáp án không sao. */
    @PutMapping("/{id}/answers/{position}")
    AnswerResponse saveAnswer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                              @PathVariable int position, @Valid @RequestBody AnswerRequest request) {
        return attemptService.saveAnswer(user.id(), id, position, request.selectedOption());
    }

    /** Bỏ chọn một câu. */
    @DeleteMapping("/{id}/answers/{position}")
    ResponseEntity<Void> clearAnswer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                     @PathVariable int position) {
        attemptService.clearAnswer(user.id(), id, position);
        return ResponseEntity.noContent().build();
    }

    /** Nộp bài; response có điểm. */
    @PostMapping("/{id}/submit")
    AttemptResponse submit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return attemptService.submit(user.id(), id);
    }
}
