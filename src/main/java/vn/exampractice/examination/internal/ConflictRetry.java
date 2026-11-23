package vn.exampractice.examination.internal;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Chạy một thao tác ghi lên bài làm trong transaction, tự thử lại khi đụng thao tác ghi song song
 * (ADR-0017): tối đa {@value #MAX_TRIES} lần, mỗi lần một transaction mới, nên mỗi lần thử tải lại dữ
 * liệu và kiểm tra lại luật từ đầu.
 *
 * <p>Hai dấu hiệu được coi là xung đột:
 * <ul>
 *   <li>{@link OptimisticLockingFailureException}: version đã bị thao tác khác tăng.</li>
 *   <li>{@link DataIntegrityViolationException}: hai request cùng thêm một dòng, ví dụ lưu đáp án đầu
 *       tiên của cùng một câu, hoặc bấm "Bắt đầu" hai lần; lần thử lại sẽ thấy dòng đã có.</li>
 * </ul>
 * Lỗi nghiệp vụ ({@code BusinessException}) không thử lại. Thử hết lần vẫn xung đột thì ném lỗi cuối
 * cùng; optimistic locking được {@code GlobalExceptionHandler} trả thành {@code CONCURRENT_MODIFICATION}.
 *
 * <p>Không gọi từ bên trong một transaction đang mở: khi đó {@link TransactionTemplate} dùng lại
 * transaction ngoài, và transaction đã lỗi thì không thử lại được.
 */
@Component
class ConflictRetry {

    static final int MAX_TRIES = 3;

    private static final Logger log = LoggerFactory.getLogger(ConflictRetry.class);

    private final TransactionTemplate transaction;

    ConflictRetry(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
    }

    <T> T run(Supplier<T> work) {
        RuntimeException conflict = null;
        for (int tryNo = 1; tryNo <= MAX_TRIES; tryNo++) {
            try {
                return transaction.execute(status -> work.get());
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                conflict = e;
                log.debug("Xung đột ghi đồng thời, lần thử {}/{}: {}", tryNo, MAX_TRIES, e.getMessage());
            }
        }
        throw conflict;
    }

    void run(Runnable work) {
        run(() -> {
            work.run();
            return null;
        });
    }
}
