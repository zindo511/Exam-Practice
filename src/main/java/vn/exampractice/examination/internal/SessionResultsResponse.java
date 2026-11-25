package vn.exampractice.examination.internal;

import vn.exampractice.shared.web.PageResponse;

/**
 * Bảng điểm của một kỳ thi cho giáo viên: tóm tắt cả kỳ thi và một trang danh sách học sinh.
 *
 * @param totalQuestions số câu trong bản chụp đề; 0 nếu kỳ thi chưa mở
 * @param summary        tính trên cả kỳ thi, không chỉ trang hiện tại
 */
record SessionResultsResponse(
        Long sessionId,
        String title,
        SessionStatus status,
        int maxScore,
        int totalQuestions,
        ResultSummary summary,
        PageResponse<StudentResultRow> students) {
}
