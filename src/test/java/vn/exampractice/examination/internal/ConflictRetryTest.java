package vn.exampractice.examination.internal;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.exampractice.shared.error.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cơ chế thử lại khi xung đột (ADR-0017), với transaction giả để đếm số lần thử. */
class ConflictRetryTest {

    private final CountingTransactionManager transactions = new CountingTransactionManager();
    private final ConflictRetry retry = new ConflictRetry(transactions);
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void retries_in_a_new_transaction_until_it_succeeds() {
        String result = retry.run(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new ObjectOptimisticLockingFailureException(Attempt.class, 1L);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(transactions.begun).isEqualTo(3);
        assertThat(transactions.rolledBack).isEqualTo(2);
        assertThat(transactions.committed).isEqualTo(1);
    }

    @Test
    void gives_up_after_3_tries() {
        assertThatThrownBy(() -> retry.run(() -> {
            calls.incrementAndGet();
            throw new ObjectOptimisticLockingFailureException(Attempt.class, 1L);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(calls).hasValue(ConflictRetry.MAX_TRIES);
    }

    /** Hai request cùng thêm một dòng: lần thử lại sẽ thấy dòng đã có. */
    @Test
    void duplicate_insert_is_treated_as_a_conflict() {
        retry.run(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new DataIntegrityViolationException("duplicate key");
            }
        });

        assertThat(calls).hasValue(2);
    }

    @Test
    void business_errors_are_not_retried() {
        assertThatThrownBy(() -> retry.run(() -> {
            calls.incrementAndGet();
            throw new BusinessException(ExaminationErrorCode.ATTEMPT_EXPIRED);
        })).isInstanceOf(BusinessException.class);

        assertThat(calls).hasValue(1);
    }

    private static final class CountingTransactionManager implements PlatformTransactionManager {
        int begun;
        int committed;
        int rolledBack;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            begun++;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            committed++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rolledBack++;
        }
    }
}
