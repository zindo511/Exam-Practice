# ADR-0017: Thử lại khi xung đột optimistic locking trên bài làm

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-18
- **Bổ sung cho:** [ADR-0013](0013-xu-ly-het-gio-lam-bai.md) (mục "Optimistic locking")

## Bối cảnh

[ADR-0013](0013-xu-ly-het-gio-lam-bai.md) cho `Attempt` cột `@Version`: khi hai thao tác cùng ghi lên một bài làm, thao tác đến sau bị từ chối với `CONCURRENT_MODIFICATION` (409). Áp nguyên như vậy thì có hai vấn đề:

- **Học sinh bấm chọn đáp án liên tiếp rất nhanh**, hoặc trình duyệt gửi lại request khi mạng chập chờn: hai request lưu đáp án chạy song song là chuyện bình thường, không phải lỗi. Trả 409 khiến học sinh thấy lỗi dù không làm gì sai.
- **Sửa một `Answer` đã có không làm tăng `version` của `Attempt`.** Hibernate chỉ tăng version của cha khi chính nó đổi field hoặc khi danh sách con thêm/bớt phần tử. Hai request đổi cùng một câu cùng lúc sẽ không bị phát hiện; hai request lưu cùng một câu lần đầu cùng lúc sẽ đụng `UNIQUE (attempt_id, session_question_id)` và thành lỗi 500.

## Quyết định

- **Mọi thao tác ghi lên bài làm đều tăng `version` của `Attempt`.** Service tải bài làm để ghi bằng khóa `OPTIMISTIC_FORCE_INCREMENT`, nên kể cả khi chỉ sửa một `Answer`, Hibernate vẫn tăng version lúc commit và phát hiện được thao tác chạy song song.
- **Thao tác của học sinh và giáo viên tự thử lại khi xung đột:** tối đa 3 lần thử, mỗi lần trong **một transaction mới** (`TransactionTemplate`, không bọc `@Transactional` quanh vòng lặp, vì transaction đã lỗi thì không dùng lại được). Mỗi lần thử tải lại bài làm và **kiểm tra lại luật** (còn đang làm, chưa quá hạn chót), nên lần thử lại không bao giờ ghi đè một bài vừa được nộp hay chốt.
- **Thêm trùng dòng cũng là xung đột:** hai request cùng lưu đáp án đầu tiên của một câu, hoặc bấm "Bắt đầu" hai lần, đụng ràng buộc `UNIQUE`; lần thử lại tải lại dữ liệu và thấy dòng đã có (sửa đáp án, hoặc báo `ATTEMPT_ALREADY_STARTED`).
- **Thử 3 lần vẫn xung đột** thì trả `CONCURRENT_MODIFICATION` (409) như ADR-0013.
- **Lỗi nghiệp vụ không thử lại:** `ATTEMPT_EXPIRED`, `ATTEMPT_ALREADY_SUBMITTED`… trả ngay ở lần đầu.
- **Job chốt bài hết giờ không thử lại:** gặp xung đột thì ghi log và bỏ qua bài đó; lượt chạy sau (khoảng 30 giây) xử lý tiếp.
- **Giao diện gửi request lưu đáp án lần lượt** cho mỗi bài làm, chỉ gửi lựa chọn mới nhất, để giảm xung đột ngay từ đầu.

## Phương án đã cân nhắc

- **Không thử lại, trả 409 như ADR-0013:** học sinh thấy lỗi khi bấm nhanh, phải tự tải lại trang.
- **Khóa bi quan (`SELECT ... FOR UPDATE`):** không có xung đột, nhưng giữ khóa dòng trong suốt transaction; request lưu đáp án và job phải xếp hàng chờ nhau, dễ chậm khi cả lớp thi cùng lúc.
- **Bọc `@Transactional` quanh vòng lặp thử lại:** transaction bị đánh dấu rollback sau lần lỗi đầu tiên, các lần thử sau không commit được.

## Hệ quả

- (+) Học sinh bấm nhanh hoặc gửi trùng request không thấy lỗi; đáp án cuối cùng được lưu.
- (+) Không bao giờ ghi đè bài đã nộp hoặc đã chốt, vì luật được kiểm tra lại ở mỗi lần thử.
- (+) Có test chạy request song song thật (`AttemptConcurrencyTest`); bỏ thử lại hoặc bỏ khóa `OPTIMISTIC_FORCE_INCREMENT` thì test thất bại (điểm đã chấm lệch với đáp án đã lưu).
- (−) Mỗi lần lưu đáp án ghi thêm một câu `UPDATE` lên `exam_attempt` để tăng version.
- (−) Service ghi bài làm dùng `TransactionTemplate` thay vì `@Transactional`, khác các service khác.
- Xem lại khi số học sinh thi cùng lúc lớn tới mức xung đột thường xuyên vượt quá 3 lần thử.
