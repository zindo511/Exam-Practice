package vn.exampractice.examination.internal;

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.exampractice.shared.security.AuthenticatedUser;
import vn.exampractice.shared.web.PageResponse;

/**
 * Kỳ thi phía học sinh: nhập mã tham gia và phòng chờ. Chỉ STUDENT gọi được (giáo viên quản lý kỳ
 * thi qua ExamSessionController).
 */
@RestController
@RequestMapping("/api/exam")
@PreAuthorize("hasRole('STUDENT')")
class StudentSessionController {

    private final StudentSessionService studentSessionService;

    StudentSessionController(StudentSessionService studentSessionService) {
        this.studentSessionService = studentSessionService;
    }

    /**
     * Nhập mã để vào phòng chờ. Nhập lại mã của kỳ thi đã tham gia vẫn trả về kỳ thi đó. Sai quá 5 lần
     * trong 15 phút thì bị tạm chặn (429).
     */
    @PostMapping("/sessions/join")
    MySessionResponse join(@AuthenticationPrincipal AuthenticatedUser user,
                           @Valid @RequestBody JoinRequest request) {
        return studentSessionService.join(user.id(), request.accessCode());
    }

    /** "Kỳ thi của tôi": các kỳ thi đã tham gia, mặc định giờ bắt đầu muộn nhất trước. */
    @GetMapping("/my-sessions")
    PageResponse<MySessionResponse> mySessions(@AuthenticationPrincipal AuthenticatedUser user,
                                               @ParameterObject Pageable pageable) {
        return studentSessionService.mySessions(user.id(), pageable);
    }

    /** Phòng chờ: giao diện gọi định kỳ để biết khi nào kỳ thi chuyển sang OPEN. */
    @GetMapping("/my-sessions/{id}")
    MySessionResponse mySession(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return studentSessionService.mySession(user.id(), id);
    }
}
