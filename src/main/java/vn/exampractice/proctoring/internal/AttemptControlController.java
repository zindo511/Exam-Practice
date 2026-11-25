package vn.exampractice.proctoring.internal;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.exampractice.shared.security.AuthenticatedUser;

/** Giáo viên can thiệp vào bài đang làm. Chỉ TEACHER gọi được; chỉ với bài trong kỳ thi của chính mình. */
@RestController
@RequestMapping("/api/proctor/attempts/{attemptId}")
@PreAuthorize("hasRole('TEACHER')")
class AttemptControlController {

    private final AttemptControlService attemptControlService;

    AttemptControlController(AttemptControlService attemptControlService) {
        this.attemptControlService = attemptControlService;
    }

    /** Cộng giờ, ví dụ khi học sinh báo bị mất mạng; hạn chót mới được vượt giờ đóng kỳ thi (ADR-0018). */
    @PostMapping("/extend-time")
    AttemptControlResponse extendTime(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long attemptId,
                                      @Valid @RequestBody ExtendTimeRequest request) {
        return attemptControlService.extendTime(user.id(), attemptId, request.minutes());
    }

    /** Khóa bài vì vi phạm: bài kết thúc ngay và được chấm, không mở lại được (ADR-0012). */
    @PostMapping("/lock")
    AttemptControlResponse lock(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long attemptId) {
        return attemptControlService.lock(user.id(), attemptId);
    }
}
