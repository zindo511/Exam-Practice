package vn.exampractice.examination.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phần tóm tắt trên bảng điểm của giáo viên.
 *
 * @param participantCount số học sinh đã nhập mã vào kỳ thi
 * @param notStartedCount  đã vào phòng chờ nhưng chưa bắt đầu làm bài
 * @param inProgressCount  đang làm bài
 * @param finishedCount    bài đã kết thúc (nộp, hết giờ hoặc bị khóa), đã có điểm
 * @param averageScore     điểm trung bình của các bài đã kết thúc, 2 chữ số thập phân; rỗng nếu chưa có bài nào
 * @param highestScore     rỗng nếu chưa có bài nào kết thúc
 * @param lowestScore      rỗng nếu chưa có bài nào kết thúc
 */
record ResultSummary(
        long participantCount,
        long notStartedCount,
        long inProgressCount,
        long finishedCount,
        BigDecimal averageScore,
        BigDecimal highestScore,
        BigDecimal lowestScore) {

    /**
     * @param participantCount số học sinh đã tham gia
     * @param attempts         mọi bài làm của kỳ thi
     */
    static ResultSummary of(long participantCount, List<Attempt> attempts) {
        long inProgress = 0;
        List<BigDecimal> scores = new ArrayList<>();
        for (Attempt attempt : attempts) {
            if (attempt.getStatus().isFinished()) {
                scores.add(attempt.getScore());
            } else {
                inProgress++;
            }
        }
        long notStarted = participantCount - attempts.size();

        if (scores.isEmpty()) {
            return new ResultSummary(participantCount, notStarted, inProgress, 0, null, null, null);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal score : scores) {
            total = total.add(score);
        }
        BigDecimal average = total.divide(BigDecimal.valueOf(scores.size()), Grading.SCORE_SCALE, RoundingMode.HALF_UP);
        return new ResultSummary(participantCount, notStarted, inProgress, scores.size(),
                average, Collections.max(scores), Collections.min(scores));
    }
}
