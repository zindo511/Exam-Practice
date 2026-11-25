package vn.exampractice.proctoring.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.exampractice.shared.security.AuthenticatedUser;

/** Trình duyệt học sinh gửi tín hiệu giám sát. Chỉ STUDENT gọi được; chỉ gửi cho bài làm của chính mình. */
@RestController
@RequestMapping("/api/proctor/attempts/{attemptId}")
@PreAuthorize("hasRole('STUDENT')")
class ProctoringController {

    private final HeartbeatService heartbeatService;

    ProctoringController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    /** Báo học sinh còn ở đó, trình duyệt tự gọi 15 giây một lần; 204 vì không cần trả dữ liệu (README 6.4). */
    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void heartbeat(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long attemptId,
                   @Valid @RequestBody HeartbeatRequest request) {
        heartbeatService.heartbeat(user.id(), attemptId, request.currentQuestion());
    }
}
