-- Index cho job mở và đóng kỳ thi (ADR-0013). Partial index: job chỉ tìm trong các kỳ thi chưa kết thúc,
-- số dòng nhỏ dù bảng lớn dần.

-- Job mở kỳ thi: kỳ thi đang chờ đã tới giờ mở.
CREATE INDEX ix_exam_session_scheduled_start ON exam_session (start_at) WHERE status = 'SCHEDULED';

-- Job đóng kỳ thi: kỳ thi đang chờ hoặc đang mở đã quá giờ đóng.
CREATE INDEX ix_exam_session_active_end ON exam_session (end_at) WHERE status IN ('SCHEDULED', 'OPEN');
