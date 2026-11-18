# ADR-0016: Mỗi câu 1 điểm, điểm bài quy về thang điểm của kỳ thi

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-17 (cập nhật 2026-11-18: thang điểm do giáo viên chọn cho từng kỳ thi thay vì luôn là 10)

## Bối cảnh

Thiết kế ban đầu cho giáo viên đặt điểm riêng cho từng câu trong đề (`design_exam_question.points`), sao chép sang kỳ thi (`exam_session_question.points`), và điểm bài là tổng điểm các câu đúng.

Khi bắt tay viết phần đề, cách này lộ ra mấy vấn đề:
- Phần lớn đề ở Việt Nam chấm theo thang 10 và các câu ngang điểm nhau, nhưng điểm từng câu không phải lúc nào cũng chia hết. Đề 30 câu thì mỗi câu được 0.333..., không lưu được chính xác bằng số thập phân; giáo viên phải tự chia lệch (vài câu 0.34, còn lại 0.33) để tổng đúng bằng 10.
- Phải tự đặt thêm các giới hạn (điểm tối đa, số chữ số thập phân) mà tài liệu không có.
- Giáo viên phải nhập điểm cho từng câu mỗi lần ghép đề.

MVP chỉ có câu trắc nghiệm **một đáp án đúng**: mỗi câu chỉ có hai kết quả, đúng hoặc sai. Chưa có câu nhiều đáp án hay tự luận cần chấm một phần điểm.

## Quyết định

- **Mỗi câu 1 điểm:** đúng được 1, sai hoặc bỏ trống được 0. Đề và kỳ thi không lưu điểm theo câu (bỏ cột `points` ở `design_exam_question` và `exam_session_question`).
- **Điểm bài quy về thang điểm của kỳ thi:** giáo viên chọn thang (`max_score`, số nguyên 1–100, mặc định 10) khi tạo kỳ thi; thang cố định từ lúc tạo. `score = số câu đúng / tổng số câu × max_score`, làm tròn 2 chữ số thập phân theo kiểu làm tròn lên từ 5 (`HALF_UP`). Ví dụ thang 10, đúng 7/30 câu được 2.33; thang 20 thì được 4.67.
- **Bài làm lưu cả `correct_count` và `score`:** giáo viên thấy được "đúng 21/30" chứ không chỉ "7.00"; nếu sau này đổi cách quy đổi vẫn tính lại được từ số câu đúng.
- Giáo viên ghép đề chỉ cần chọn câu và sắp thứ tự; `PUT /api/design/exams/{id}/questions` nhận danh sách `questionId`.

## Phương án đã cân nhắc

- **Điểm riêng từng câu, điểm bài là tổng điểm câu đúng:** linh hoạt nhất, nhưng vướng các vấn đề ở phần Bối cảnh, trong khi MVP chưa có loại câu nào cần điểm khác nhau.
- **Trọng số từng câu, quy về thang 10 khi chấm:** giải quyết được chuyện chia không hết và vẫn cho câu khó nặng hơn, nhưng thêm một trường giáo viên phải nhập mà MVP chưa cần. Đây là hướng mở rộng khi cần (xem Hệ quả).
- **Luôn quy về thang 10:** đơn giản nhất, nhưng giáo viên dùng thang 20 hoặc 100 phải tự quy đổi lại.
- **Thang điểm gắn với đề thay vì kỳ thi:** một đề có thể dùng cho nhiều kỳ thi với thang khác nhau; thang là cách công bố kết quả của một buổi thi, nên thuộc về kỳ thi.

## Hệ quả

- (+) Không còn chuyện điểm chia không hết; giáo viên ghép đề nhanh hơn.
- (+) Chấm điểm đơn giản: đếm câu đúng rồi quy đổi một lần.
- (+) Snapshot đề và bảng câu hỏi trong kỳ thi gọn hơn.
- (−) Không cho câu khó nặng điểm hơn câu dễ.
- (−) Điểm làm tròn 2 chữ số, nên điểm của các bài có thể bằng nhau dù số câu đúng khác nhau rất ít ở đề rất dài; chấp nhận vì đề tối đa 100 câu.
- Xem lại khi có câu nhiều đáp án, tự luận, hoặc giáo viên cần câu nặng nhẹ khác nhau: thêm cột trọng số với mặc định 1 cho `design_exam_question` và `exam_session_question`, đổi công thức thành tổng trọng số câu đúng chia tổng trọng số. Dữ liệu cũ vẫn đúng vì mọi câu có trọng số 1.
