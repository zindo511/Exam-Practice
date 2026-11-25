package vn.exampractice.examination.internal;

import java.net.URI;

import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import vn.exampractice.shared.security.AuthenticatedUser;
import vn.exampractice.shared.web.PageResponse;

/**
 * Quản lý kỳ thi của giáo viên. Tiền tố {@code /api/exam/**} cho cả STUDENT và TEACHER
 * (SecurityConfig), nên controller này tự giới hạn TEACHER bằng {@code @PreAuthorize}; quyền theo dữ
 * liệu (chỉ kỳ thi của mình) do ExamSessionService kiểm tra.
 *
 * <p>Các thao tác đổi trạng thái là hành động, không phải CRUD, nên dùng {@code POST} với động từ đặt
 * sau tài nguyên (README 6.3).
 */
@RestController
@RequestMapping("/api/exam/sessions")
@PreAuthorize("hasRole('TEACHER')")
class ExamSessionController {

    private final ExamSessionService sessionService;
    private final SessionResultService resultService;

    ExamSessionController(ExamSessionService sessionService, SessionResultService resultService) {
        this.sessionService = sessionService;
        this.resultService = resultService;
    }

    /** 201 kèm header {@code Location} trỏ tới kỳ thi vừa tạo (README 6.4). */
    @PostMapping
    ResponseEntity<SessionResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody SessionRequest request) {
        SessionResponse created = sessionService.create(user.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Mặc định giờ bắt đầu muộn nhất trước; {@code ?status=} lọc theo trạng thái. */
    @GetMapping
    PageResponse<SessionResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestParam(required = false) SessionStatus status,
                                       @ParameterObject Pageable pageable) {
        return sessionService.list(user.id(), status, pageable);
    }

    @GetMapping("/{id}")
    SessionResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return sessionService.get(user.id(), id);
    }

    /** Mở kỳ thi và chụp snapshot đề; được mở sớm trước giờ bắt đầu. */
    @PostMapping("/{id}/open")
    SessionResponse open(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return sessionService.open(user.id(), id);
    }

    /** Kết thúc sớm kỳ thi đang mở. */
    @PostMapping("/{id}/close")
    SessionResponse close(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return sessionService.close(user.id(), id);
    }

    /** Hủy kỳ thi chưa mở. */
    @PostMapping("/{id}/cancel")
    SessionResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return sessionService.cancel(user.id(), id);
    }

    /** Đổi những gì học sinh được xem sau khi bài kết thúc, ví dụ công bố điểm sau khi kỳ thi đóng. */
    @PutMapping("/{id}/result-visibility")
    SessionResponse changeResultVisibility(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                           @Valid @RequestBody ResultVisibilityRequest request) {
        return sessionService.changeResultVisibility(user.id(), id, request.resultVisibility());
    }

    @PostMapping("/{id}/lock-join")
    SessionResponse lockJoin(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return sessionService.lockJoin(user.id(), id);
    }

    @PostMapping("/{id}/unlock-join")
    SessionResponse unlockJoin(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return sessionService.unlockJoin(user.id(), id);
    }

    /**
     * Bảng điểm: mọi học sinh đã tham gia (kể cả chưa làm bài) kèm điểm, và tóm tắt cả kỳ thi. Luôn xếp
     * điểm cao trước, chưa có điểm xuống cuối; {@code ?status=} lọc theo trạng thái, {@code NOT_STARTED}
     * là chưa làm bài.
     */
    @GetMapping("/{id}/results")
    SessionResultsResponse results(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                   @RequestParam(required = false) ResultFilter status,
                                   @ParameterObject Pageable pageable) {
        return resultService.results(user.id(), id, status, pageable);
    }

    /** Chi tiết một bài làm: từng câu học sinh chọn gì và đáp án đúng. */
    @GetMapping("/{id}/attempts/{attemptId}")
    TeacherAttemptResponse attempt(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                   @PathVariable Long attemptId) {
        return resultService.attempt(user.id(), id, attemptId);
    }
}
