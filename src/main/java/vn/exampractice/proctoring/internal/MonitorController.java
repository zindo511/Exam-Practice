package vn.exampractice.proctoring.internal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.exampractice.shared.security.AuthenticatedUser;

/** Màn hình giám sát. Chỉ TEACHER gọi được; chỉ với kỳ thi của chính mình. */
@RestController
@RequestMapping("/api/proctor/sessions/{sessionId}")
@PreAuthorize("hasRole('TEACHER')")
class MonitorController {

    private final MonitorService monitorService;

    MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /** Bảng giám sát cả lớp; trình duyệt giáo viên gọi khoảng 5 giây một lần (ADR-0011). */
    @GetMapping
    MonitorResponse monitor(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long sessionId) {
        return monitorService.monitor(user.id(), sessionId);
    }
}
