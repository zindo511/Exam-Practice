import { http } from '../../shared/http';
import type { PageResponse } from '../../shared/page';

export type Question = {
    id: number;
    content: string;
    options: string[];
    /** Vị trí đáp án đúng trong `options`, bắt đầu từ 0. */
    correctOption: number;
    createdAt: string;
    updatedAt: string;
};

export type QuestionInput = Pick<Question, 'content' | 'options' | 'correctOption'>;

export const PAGE_SIZE = 20;

/** Câu hỏi của giáo viên, mới nhất trước. `keyword` rỗng thì không lọc. */
export function listQuestions(page: number, keyword: string): Promise<PageResponse<Question>> {
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (keyword !== '') {
        params.set('keyword', keyword);
    }
    return http.get(`/api/design/questions?${params}`);
}

export function createQuestion(input: QuestionInput): Promise<Question> {
    return http.post('/api/design/questions', input);
}

export function updateQuestion(id: number, input: QuestionInput): Promise<Question> {
    return http.put(`/api/design/questions/${id}`, input);
}

/** Backend chỉ lưu trữ, không xóa hẳn. Câu đang nằm trong đề thì báo `QUESTION_IN_USE`. */
export function deleteQuestion(id: number): Promise<void> {
    return http.delete(`/api/design/questions/${id}`);
}
