# ADR-0013: Xử lý hết giờ làm bài

- **Trạng thái:** Đã chấp nhận; phần optimistic locking được bổ sung bởi [ADR-0017](0017-thu-lai-khi-xung-dot-optimistic-locking.md)
- **Ngày:** 2026-11-15

## Bối cảnh

Mỗi bài làm có hạn chót tính theo giờ server: lúc bắt đầu cộng thời lượng, không vượt giờ đóng kỳ thi. Khi hết giờ, nhiều học sinh sẽ không bấm nộp: không để ý, mất mạng, đã đóng trình duyệt hoặc tắt máy.

Cần đảm bảo:
- Không nhận thêm đáp án sau hạn chót.
- **Mọi bài đều được chốt và có điểm**, kể cả khi học sinh không bao giờ gửi request nào nữa.
- Không chấm một bài hai lần khi học sinh nộp đúng lúc hệ thống tự chốt.

Đáp án được tự động lưu sau mỗi lần chọn, nên tự nộp không làm mất dữ liệu.

## Quyết định

**Ba lớp xử lý:**

1. **Entity tự chặn (bắt buộc).** Mọi thao tác ghi lên `Attempt` (lưu đáp án, nộp bài, cộng giờ) gọi `ensureInProgress(now)`: bài phải đang `IN_PROGRESS` và `now` chưa quá `deadline`. Vi phạm thì ném `ATTEMPT_ALREADY_SUBMITTED` hoặc `ATTEMPT_EXPIRED`. `now` luôn là giờ server.
2. **Job định kỳ trên server tự chốt bài (bắt buộc).** `@Scheduled` trong `examination`, khoảng 30 giây một lần: tìm bài `IN_PROGRESS` đã quá hạn chót, gọi `attempt.expire(...)`: chuyển sang `EXPIRED`, chấm điểm từ đáp án đã lưu, `submittedAt = deadline`. Cùng job chuyển kỳ thi quá giờ đóng sang `CLOSED`.
3. **Trình duyệt tự nộp khi đồng hồ về 0 (trải nghiệm).** Học sinh thấy ngay màn hình đã nộp. Server không dựa vào lớp này.

**Trạng thái:**

```
IN_PROGRESS ──(học sinh bấm nộp)──▶ SUBMITTED
     │
     └──(quá hạn chót, job chốt)──▶ EXPIRED
```

Cả hai đều được chấm điểm; giáo viên phân biệt được bài tự nộp và bài hết giờ.

**Chi tiết bắt buộc:**
- **Ân hạn 5 giây** phía server cho lưu đáp án và nộp bài, bù độ trễ mạng sát giờ. Không bao giờ dùng giờ do trình duyệt gửi lên.
- **Optimistic locking:** `Attempt` có cột `@Version`. Khi học sinh nộp đúng lúc job đang chốt, thao tác đến sau bị từ chối; `GlobalExceptionHandler` chuyển lỗi này thành `CONCURRENT_MODIFICATION` (`409`).
- **Job chỉ xử lý từng bài một, mỗi bài một transaction nhỏ**, để một bài lỗi không làm hỏng cả lượt.

## Phương án đã cân nhắc

- **Chỉ chặn trong entity, không có job:** bài của học sinh đã rời đi nằm ở `IN_PROGRESS` mãi mãi, không có điểm.
- **Chỉ dựa vào trình duyệt tự nộp:** trình duyệt có thể đã đóng, mất mạng hoặc bị chỉnh sửa.
- **Chốt bài "lười" khi có người đọc** (gặp bài quá hạn thì chốt luôn lúc đọc): không cần job, nhưng phép đọc lại có tác dụng ghi, và thống kê có thể thấy bài chưa chốt.
- **Hẹn giờ riêng cho từng bài** (một tác vụ chạy đúng thời điểm hạn chót): chính xác từng giây, nhưng mất hết khi ứng dụng khởi động lại; job quét định kỳ đơn giản và tự phục hồi.

## Hệ quả

- (+) Mọi bài đều được chốt và chấm, không phụ thuộc vào việc học sinh còn kết nối.
- (+) Không thể ghi đáp án sau giờ, kể cả khi job chưa kịp chạy.
- (+) Không chấm trùng nhờ kiểm tra trạng thái và `@Version`.
- (−) Bài hết giờ có thể nằm ở `IN_PROGRESS` thêm tối đa khoảng 30 giây trước khi được chốt. Màn hình giám sát tính "còn lại 0 phút" từ hạn chót nên vẫn hiển thị đúng.
- (−) Khi chạy nhiều instance, mỗi instance đều chạy job. Kiểm tra trạng thái và `@Version` đã chặn chấm trùng; nếu cần tránh việc chạy thừa, thêm ShedLock.
