package vn.exampractice.examination.internal;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import vn.exampractice.shared.security.AuthenticatedUser;

/** Học sinh làm bài. Chỉ STUDENT gọi được; chỉ thao tác trên bài làm của chính mình. */
@RestController
@RequestMapping("/api/exam")
@PreAuthorize("hasRole('STUDENT')")
class AttemptController {

    private final AttemptService attemptService;

    AttemptController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    /** Bắt đầu làm bài: 201 kèm header {@code Location} trỏ tới bài làm (README 6.4). */
    @PostMapping("/sessions/{sessionId}/attempts")
    ResponseEntity<AttemptResponse> start(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable Long sessionId) {
        AttemptResponse attempt = attemptService.start(user.id(), sessionId);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/exam/attempts/{id}").buildAndExpand(attempt.id()).toUri();
        return ResponseEntity.created(location).body(attempt);
    }

    /** Tải lại bài làm: câu hỏi, đáp án đã chọn, hạn chót; kết quả theo cài đặt của kỳ thi. */
    @GetMapping("/attempts/{id}")
    AttemptResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return attemptService.get(user.id(), id);
    }

    /** Tự động lưu mỗi lần chọn đáp án; 204 vì không cần trả dữ liệu (README 6.4). */
    @PutMapping("/attempts/{id}/answers/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void saveAnswer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                    @PathVariable Long questionId, @Valid @RequestBody AnswerRequest request) {
        attemptService.saveAnswer(user.id(), id, questionId, request.selectedOption());
    }

    /** Nộp bài và chấm ngay. */
    @PostMapping("/attempts/{id}/submit")
    AttemptResponse submit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return attemptService.submit(user.id(), id);
    }
}
