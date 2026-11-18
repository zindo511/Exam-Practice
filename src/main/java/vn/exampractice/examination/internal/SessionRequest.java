package vn.exampractice.examination.internal;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body của {@code POST /api/exam/sessions}: đề dùng cho kỳ thi, giờ mở và giờ đóng. Tiêu đề được
 * sao chép từ đề, thời lượng chụp từ đề lúc mở.
 *
 * @param startAt          lúc hệ thống tự mở kỳ thi; giáo viên vẫn mở sớm được
 * @param endAt            lúc kỳ thi đóng; phải ở tương lai
 * @param resultVisibility học sinh được xem gì sau khi bài kết thúc; đổi được sau
 * @param maxScore         thang điểm 1–100; bỏ trống là 10. Không đổi được sau khi tạo
 */
record SessionRequest(
        @NotNull(message = "Không được để trống")
        Long examId,

        @NotNull(message = "Không được để trống")
        Instant startAt,

        @NotNull(message = "Không được để trống")
        @Future(message = "Phải ở tương lai")
        Instant endAt,

        @NotNull(message = "Không được để trống")
        ResultVisibility resultVisibility,

        @Min(value = ExamSession.MIN_MAX_SCORE, message = "Thang điểm từ 1 đến 100")
        @Max(value = ExamSession.MAX_MAX_SCORE, message = "Thang điểm từ 1 đến 100")
        Integer maxScore) {

    /** Thang điểm thật sự dùng: giá trị gửi lên, hoặc 10 nếu bỏ trống. */
    int maxScoreOrDefault() {
        return maxScore == null ? ExamSession.DEFAULT_MAX_SCORE : maxScore;
    }

    /** Khi một trong hai mốc còn trống thì bỏ qua, vì lỗi đã được báo ở field đó. */
    @AssertTrue(message = "Giờ mở phải trước giờ đóng")
    boolean isStartBeforeEnd() {
        return startAt == null || endAt == null || startAt.isBefore(endAt);
    }
}
