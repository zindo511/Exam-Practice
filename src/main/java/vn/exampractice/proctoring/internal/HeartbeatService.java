package vn.exampractice.proctoring.internal;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.examination.ExaminationApi;
import vn.exampractice.examination.dto.AttemptRef;
import vn.exampractice.shared.error.BusinessException;

/**
 * Nhận heartbeat từ trình duyệt học sinh (ADR-0011).
 *
 * <p>Chỉ heartbeat đầu tiên của một bài làm hỏi {@link ExaminationApi} xem bài có đúng của học sinh này
 * không; các heartbeat sau so với {@code studentId} đã lưu trên {@link Presence}.
 *
 * <p>Không kiểm tra bài còn đang làm hay đã kết thúc: màn hình giám sát lấy trạng thái bài từ examination
 * và chỉ tính online cho bài đang làm.
 */
@Service
class HeartbeatService {

    private final PresenceRepository presenceRepository;
    private final ExaminationApi examinationApi;

    HeartbeatService(PresenceRepository presenceRepository, ExaminationApi examinationApi) {
        this.presenceRepository = presenceRepository;
        this.examinationApi = examinationApi;
    }

    /**
     * Ghi nhận học sinh còn ở đó. Bài làm không tồn tại hoặc của học sinh khác báo
     * {@code ATTEMPT_NOT_FOUND}.
     *
     * <p>Hai heartbeat đầu tiên đến đúng cùng lúc (ví dụ mở hai tab) thì một cái đụng khóa chính và lỗi.
     * Chấp nhận được: dữ liệu không sai, và heartbeat kế tiếp chạy bình thường.
     */
    @Transactional
    void heartbeat(Long studentId, Long attemptId, Integer currentQuestion) {
        Instant now = Instant.now();
        Optional<Presence> existing = presenceRepository.findById(attemptId);
        if (existing.isEmpty()) {
            AttemptRef attempt = examinationApi.findAttemptOfStudent(attemptId, studentId)
                    .orElseThrow(() -> new BusinessException(ProctoringErrorCode.ATTEMPT_NOT_FOUND));
            Presence firstPresence = Presence.firstSeen(attempt.attemptId(), attempt.sessionId(),
                    attempt.studentId(), currentQuestion, now);
            presenceRepository.save(firstPresence);
            return;
        }
        Presence presence = existing.get();
        if (!presence.getStudentId().equals(studentId)) {
            throw new BusinessException(ProctoringErrorCode.ATTEMPT_NOT_FOUND);
        }
        presence.seen(currentQuestion, now);
    }
}
