import { http } from '../../shared/http';
import type { PageResponse } from '../../shared/page';

/** Thông tin tóm tắt của đề, dùng ở danh sách. */
export type Exam = {
    id: number;
    title: string;
    durationMinutes: number;
    questionCount: number;
    createdAt: string;
    updatedAt: string;
};

/** Một câu trong đề; `position` bắt đầu từ 1. */
export type ExamQuestionItem = {
    position: number;
    questionId: number;
    content: string;
    options: string[];
    correctOption: number;
};

export type ExamDetail = Exam & { questions: ExamQuestionItem[] };

export type ExamInput = { title: string; durationMinutes: number };

export const PAGE_SIZE = 20;

/** Đề của giáo viên, mới nhất trước. `keyword` rỗng thì không lọc. */
export function listExams(page: number, keyword: string): Promise<PageResponse<Exam>> {
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (keyword !== '') {
        params.set('keyword', keyword);
    }
    return http.get(`/api/design/exams?${params}`);
}

export function getExam(id: number): Promise<ExamDetail> {
    return http.get(`/api/design/exams/${id}`);
}

/** Đề mới chưa có câu nào; thêm câu bằng `replaceExamQuestions`. */
export function createExam(input: ExamInput): Promise<Exam> {
    return http.post('/api/design/exams', input);
}

export function updateExam(id: number, input: ExamInput): Promise<Exam> {
    return http.put(`/api/design/exams/${id}`, input);
}

/** Thay toàn bộ danh sách câu; thứ tự trong mảng là thứ tự câu. Câu không dùng được báo `QUESTION_NOT_AVAILABLE`. */
export function replaceExamQuestions(id: number, questionIds: number[]): Promise<ExamDetail> {
    return http.put(`/api/design/exams/${id}/questions`, { questionIds });
}

/** Backend chỉ lưu trữ. Kỳ thi đã lên lịch từ đề này sẽ không mở được. */
export function deleteExam(id: number): Promise<void> {
    return http.delete(`/api/design/exams/${id}`);
}
