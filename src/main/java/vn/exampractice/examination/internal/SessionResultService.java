package vn.exampractice.examination.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.identity.IdentityApi;
import vn.exampractice.identity.dto.UserSummary;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.web.PageResponse;

/**
 * Giáo viên xem kết quả kỳ thi: bảng điểm và chi tiết từng bài làm. Chỉ đọc.
 *
 * <p>Chỉ giáo viên sở hữu kỳ thi mới xem được; kỳ thi của người khác báo {@code SESSION_NOT_FOUND}
 * (404), giống {@link ExamSessionService}. Tên học sinh lấy qua {@link IdentityApi}, gom ID của cả
 * trang gọi một lần (ADR-0007).
 */
@Service
@Transactional(readOnly = true)
class SessionResultService {

    /** Thứ tự mặc định của bảng điểm: ai vào phòng chờ trước đứng trước. */
    private static final Sort JOINED_FIRST = Sort.by(Sort.Order.asc("joinedAt"), Sort.Order.asc("id"));

    private final ExamSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final AttemptRepository attemptRepository;
    private final IdentityApi identityApi;

    SessionResultService(ExamSessionRepository sessionRepository,
                         SessionParticipantRepository participantRepository,
                         AttemptRepository attemptRepository,
                         IdentityApi identityApi) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.attemptRepository = attemptRepository;
        this.identityApi = identityApi;
    }

    /**
     * Bảng điểm: mọi học sinh đã nhập mã, kể cả người chưa bắt đầu làm bài, kèm bài làm và điểm.
     *
     * <ol>
     *   <li>Lấy một trang học sinh đã tham gia.</li>
     *   <li>Lấy bài làm của các học sinh trong trang bằng một truy vấn.</li>
     *   <li>Lấy tên của các học sinh trong trang bằng một lần gọi {@link IdentityApi}.</li>
     *   <li>Ghép lại thành từng dòng; tóm tắt tính trên cả kỳ thi.</li>
     * </ol>
     */
    SessionResultsResponse results(Long teacherId, Long sessionId, Pageable pageable) {
        ExamSession session = findOwned(teacherId, sessionId);

        Page<SessionParticipant> participants = participantRepository.findBySessionId(sessionId, withDefaultSort(pageable));
        List<Long> studentIds = participants.getContent().stream().map(SessionParticipant::getStudentId).toList();

        Map<Long, Attempt> attemptByStudent = new HashMap<>();
        Map<Long, UserSummary> users = Map.of();
        if (!studentIds.isEmpty()) {
            for (Attempt attempt : attemptRepository.findBySessionIdAndStudentIdIn(sessionId, studentIds)) {
                attemptByStudent.put(attempt.getStudentId(), attempt);
            }
            users = identityApi.findUsers(studentIds);
        }

        Map<Long, UserSummary> usersById = users;
        Page<StudentResultRow> rows = participants.map(participant -> StudentResultRow.from(participant,
                usersById.get(participant.getStudentId()), attemptByStudent.get(participant.getStudentId())));

        ResultSummary summary = ResultSummary.of(participantRepository.countBySessionId(sessionId),
                attemptRepository.findBySessionId(sessionId));

        return new SessionResultsResponse(session.getId(), session.getTitle(), session.getStatus(),
                session.getMaxScore(), session.getQuestions().size(), summary, PageResponse.from(rows));
    }

    /** Chi tiết một bài làm: từng câu, học sinh chọn gì, đáp án đúng. */
    TeacherAttemptResponse attempt(Long teacherId, Long sessionId, Long attemptId) {
        ExamSession session = findOwned(teacherId, sessionId);
        Attempt attempt = attemptRepository.findByIdAndSessionId(attemptId, sessionId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.ATTEMPT_NOT_FOUND));
        UserSummary user = identityApi.findUsers(List.of(attempt.getStudentId())).get(attempt.getStudentId());
        return TeacherAttemptResponse.of(attempt, session, user);
    }

    private ExamSession findOwned(Long teacherId, Long sessionId) {
        return sessionRepository.findByIdAndTeacherId(sessionId, teacherId)
                .orElseThrow(() -> new BusinessException(ExaminationErrorCode.SESSION_NOT_FOUND));
    }

    private static Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), JOINED_FIRST);
    }
}
