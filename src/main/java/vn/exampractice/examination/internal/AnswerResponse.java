package vn.exampractice.examination.internal;

import java.time.Instant;

/** Đáp án vừa lưu, kèm hạn chót để trình duyệt chỉnh lại đồng hồ theo giờ server. */
record AnswerResponse(int position, int selectedOption, Instant savedAt, Instant deadline) {
}
