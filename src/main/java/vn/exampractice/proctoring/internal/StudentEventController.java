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

/** Trình duyệt học sinh báo sự kiện giám sát. Chỉ STUDENT gọi được; chỉ gửi cho bài làm của chính mình. */
@RestController
@RequestMapping("/api/proctor/attempts/{attemptId}/events")
@PreAuthorize("hasRole('STUDENT')")
class StudentEventController {

    private final StudentEventService studentEventService;

    StudentEventController(StudentEventService studentEventService) {
        this.studentEventService = studentEventService;
    }

    /** Trình duyệt gọi một lần cho mỗi thao tác bắt được; 204 vì không cần trả dữ liệu (README 6.4). */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void report(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long attemptId,
                @Valid @RequestBody StudentEventRequest request) {
        studentEventService.report(user.id(), attemptId, request.type(), request.occurredAt());
    }
}
