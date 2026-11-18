-- Cài đặt kết quả của kỳ thi (ADR-0016):
-- result_visibility: học sinh được xem gì sau khi bài kết thúc; giáo viên đổi được bất cứ lúc nào.
--   HIDDEN: không thấy điểm; SCORE: thấy điểm; SCORE_AND_ANSWERS: thấy điểm, và đáp án sau khi kỳ thi đóng.
-- max_score: thang điểm 1-100, điểm bài = số câu đúng / tổng số câu × max_score; cố định từ lúc tạo.
-- Giá trị DEFAULT chỉ để điền cho các kỳ thi tạo trước migration này; ứng dụng luôn ghi giá trị cụ thể.
ALTER TABLE exam_session
    ADD COLUMN result_visibility VARCHAR(20) NOT NULL DEFAULT 'SCORE',
    ADD COLUMN max_score         INTEGER     NOT NULL DEFAULT 10,
    ADD CONSTRAINT ck_exam_session_result_visibility
        CHECK (result_visibility IN ('HIDDEN', 'SCORE', 'SCORE_AND_ANSWERS')),
    ADD CONSTRAINT ck_exam_session_max_score CHECK (max_score BETWEEN 1 AND 100);
