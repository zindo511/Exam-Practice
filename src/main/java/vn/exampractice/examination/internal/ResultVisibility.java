package vn.exampractice.examination.internal;

/**
 * Học sinh được xem gì sau khi bài làm kết thúc. Giáo viên chọn khi tạo kỳ thi và đổi được bất cứ lúc
 * nào, ví dụ ẩn điểm trong lúc thi rồi công bố sau. Giáo viên luôn xem được đầy đủ kết quả.
 */
enum ResultVisibility {

    /** Không thấy điểm, chỉ biết bài đã kết thúc. */
    HIDDEN,

    /** Thấy điểm và số câu đúng ngay khi bài kết thúc. */
    SCORE,

    /**
     * Thấy điểm ngay khi bài kết thúc; thấy từng câu đúng sai và đáp án đúng chỉ sau khi kỳ thi đóng,
     * để học sinh nộp sớm không gửi đáp án được cho người còn đang thi.
     */
    SCORE_AND_ANSWERS
}
