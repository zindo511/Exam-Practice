package vn.exampractice.examination.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Luật tạm chặn nhập mã kỳ thi (ADR-0010): được sai tối đa {@value #MAX_FAILURES} lần; khi
 * {@value #MAX_FAILURES} lần sai gần nhất nằm trong {@link #WINDOW 15 phút}, tài khoản bị chặn nhập
 * mã {@link #BLOCK 15 phút} tính từ lần sai cuối. Trong lúc bị chặn, request không được kiểm tra mã
 * nên cũng không ghi thêm lần sai.
 *
 * <p>Chỉ là phép tính trên danh sách thời điểm, không đọc database, để unit test kiểm soát được thời
 * gian.
 */
final class JoinAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration BLOCK = Duration.ofMinutes(15);

    /** Khoảng thời gian cần đọc lại các lần sai để quyết định có chặn không. */
    static final Duration LOOKBACK = WINDOW.plus(BLOCK);

    private JoinAttemptLimiter() {
    }

    /**
     * @param recentFailures các lần sai trong {@link #LOOKBACK}, mới nhất trước, tối đa
     *                       {@value #MAX_FAILURES} phần tử
     * @return thời điểm hết bị chặn, hoặc rỗng nếu không bị chặn
     */
    static Optional<Instant> blockedUntil(List<Instant> recentFailures, Instant now) {
        if (recentFailures.size() < MAX_FAILURES) {
            return Optional.empty();
        }
        Instant newest = recentFailures.getFirst();
        Instant fifthNewest = recentFailures.get(MAX_FAILURES - 1);
        if (Duration.between(fifthNewest, newest).compareTo(WINDOW) > 0) {
            return Optional.empty();
        }
        Instant until = newest.plus(BLOCK);
        return now.isBefore(until) ? Optional.of(until) : Optional.empty();
    }
}
