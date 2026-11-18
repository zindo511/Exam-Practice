package vn.exampractice.examination.internal;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi một lần nhập sai mã trong <b>transaction riêng</b> ({@code REQUIRES_NEW}).
 *
 * <p>Lý do: sau khi ghi, service ném {@code INVALID_ACCESS_CODE}, khiến transaction của request bị
 * rollback. Nếu ghi chung transaction đó thì lần sai cũng bị hủy và giới hạn 5 lần không bao giờ đếm
 * được. Phải là bean riêng, vì gọi hàm {@code @Transactional} trong cùng class thì không đi qua proxy
 * của Spring.
 */
@Component
class JoinFailureRecorder {

    private final JoinFailureRepository repository;

    JoinFailureRecorder(JoinFailureRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(Long studentId, Instant now) {
        repository.save(new JoinFailure(studentId, now));
    }
}
