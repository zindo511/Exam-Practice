package vn.exampractice.examination.internal;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import vn.exampractice.shared.security.AuthenticatedUser;

/**
 * Học sinh tham gia kỳ thi bằng mã và xem phòng chờ. Tài nguyên là lượt tham gia, định danh bằng
 * ID kỳ thi vì mỗi học sinh tham gia một kỳ thi một lần.
 */
@RestController
@RequestMapping("/api/exam/participations")
@PreAuthorize("hasRole('STUDENT')")
class ParticipationController {

    private final ParticipationService participationService;

    ParticipationController(ParticipationService participationService) {
        this.participationService = participationService;
    }

    /** 201 kèm {@code Location} lần đầu; 200 nếu đã tham gia rồi (nhập lại mã sau khi tải lại trang). */
    @PostMapping
    ResponseEntity<ParticipationResponse> join(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody JoinRequest request) {
        ParticipationService.JoinResult result = participationService.join(user.id(), request.accessCode());
        if (!result.created()) {
            return ResponseEntity.ok(result.participation());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sessionId}").buildAndExpand(result.participation().sessionId()).toUri();
        return ResponseEntity.created(location).body(result.participation());
    }

    /** Phòng chờ: trình duyệt hỏi định kỳ để biết kỳ thi đã mở chưa. */
    @GetMapping("/{sessionId}")
    ParticipationResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long sessionId) {
        return participationService.get(user.id(), sessionId);
    }
}
