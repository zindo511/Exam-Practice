package vn.exampractice.examination.internal;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/**
 * Body của {@code POST /api/exam/sessions}: đề dùng cho kỳ thi, giờ mở và giờ đóng. Tiêu đề được
 * sao chép từ đề, thời lượng chụp từ đề lúc mở.
 *
 * @param startAt lúc hệ thống tự mở kỳ thi; giáo viên vẫn mở sớm được
 * @param endAt   lúc kỳ thi đóng; phải ở tương lai
 */
record SessionRequest(
        @NotNull(message = "Không được để trống")
        Long examId,

        @NotNull(message = "Không được để trống")
        Instant startAt,

        @NotNull(message = "Không được để trống")
        @Future(message = "Phải ở tương lai")
        Instant endAt) {

    /** Khi một trong hai mốc còn trống thì bỏ qua, vì lỗi đã được báo ở field đó. */
    @AssertTrue(message = "Giờ mở phải trước giờ đóng")
    boolean isStartBeforeEnd() {
        return startAt == null || endAt == null || startAt.isBefore(endAt);
    }
}
