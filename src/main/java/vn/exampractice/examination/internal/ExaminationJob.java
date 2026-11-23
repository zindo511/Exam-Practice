package vn.exampractice.examination.internal;

import java.time.Instant;
import java.util.List;
import java.util.function.BiPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * Việc chạy định kỳ của examination (ADR-0013), mỗi lượt ba bước theo thứ tự:
 * <ol>
 *   <li>Mở kỳ thi đã tới giờ mở; đề không còn dùng được thì hủy kỳ thi.</li>
 *   <li>Chốt bài đã quá hạn chót cộng ân hạn.</li>
 *   <li>Đóng kỳ thi đang mở đã quá giờ đóng cộng ân hạn; kỳ thi quá giờ đóng mà chưa từng mở thì hủy.</li>
 * </ol>
 *
 * <p>Mỗi bước lấy danh sách ID rồi xử lý từng kỳ thi hoặc bài làm trong transaction riêng (qua
 * {@link ExamSessionService} và {@link AttemptService}), để một mục lỗi không làm hỏng cả lượt. Xung đột
 * optimistic locking không thử lại: bên kia đã ghi trước, lượt sau tự thấy mục đó không còn cần xử lý.
 *
 * <p>Lịch chạy nằm ở {@link ExaminationJobScheduler}; test gọi thẳng {@link #runOnce} với giờ tự chọn.
 * Chạy nhiều instance thì mỗi instance đều chạy job; kiểm tra trạng thái và {@code @Version} chặn xử lý trùng.
 */
@Component
class ExaminationJob {

    /** Số mục tối đa mỗi bước trong một lượt; phần còn lại để lượt sau. */
    static final int BATCH_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(ExaminationJob.class);

    /**
     * Số mục được xử lý trong một lượt, để ghi log và để test kiểm tra.
     *
     * @param opened kỳ thi tới giờ mở đã được mở hoặc bị hủy
     * @param closed kỳ thi quá giờ đóng đã được đóng hoặc bị hủy
     */
    record Result(int opened, int expired, int closed) {
    }

    private final ExamSessionRepository sessionRepository;
    private final AttemptRepository attemptRepository;
    private final ExamSessionService sessionService;
    private final AttemptService attemptService;

    ExaminationJob(ExamSessionRepository sessionRepository, AttemptRepository attemptRepository,
                   ExamSessionService sessionService, AttemptService attemptService) {
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
        this.sessionService = sessionService;
        this.attemptService = attemptService;
    }

    Result runOnce(Instant now) {
        Limit batch = Limit.of(BATCH_SIZE);
        Instant graceCutoff = now.minus(Attempt.GRACE);
        int opened = process("mở kỳ thi", sessionRepository.findIdsDueToOpen(now, batch),
                sessionService::openOnSchedule, now);
        int expired = process("chốt bài", attemptRepository.findIdsOverdue(graceCutoff, batch),
                attemptService::expireOverdue, now);
        int closed = process("đóng kỳ thi", sessionRepository.findIdsDueToClose(graceCutoff, batch),
                sessionService::closeOnSchedule, now);
        Result result = new Result(opened, expired, closed);
        if (opened + expired + closed > 0) {
            log.info("Job examination: tới giờ mở {} kỳ thi, chốt {} bài, quá giờ đóng {} kỳ thi", opened, expired, closed);
        }
        return result;
    }

    private static int process(String action, List<Long> ids, BiPredicate<Long, Instant> handler, Instant now) {
        int done = 0;
        for (Long id : ids) {
            try {
                if (handler.test(id, now)) {
                    done++;
                } else {
                    log.debug("Job examination: bỏ qua {} {} (không còn cần xử lý hoặc chưa xử lý được)", action, id);
                }
            } catch (OptimisticLockingFailureException e) {
                log.debug("Job examination: {} {} xung đột ghi đồng thời, để lượt sau", action, id);
            } catch (RuntimeException e) {
                log.warn("Job examination: {} {} lỗi, để lượt sau", action, id, e);
            }
        }
        return done;
    }
}
