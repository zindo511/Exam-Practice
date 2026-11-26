import { http } from '../../shared/http';
import type { AttemptStatus } from '../sessions/api';

/** Một câu trong bài làm. `correctOption` và `correct` chỉ có khi `answersRevealed`. */
export type AttemptQuestion = {
    questionId: number;
    position: number;
    content: string;
    options: string[];
    selectedOption: number | null;
    correctOption: number | null;
    correct: boolean | null;
};

/**
 * Bài làm của học sinh. Không bao giờ có đáp án đúng khi đang làm. `serverTime` là giờ server lúc trả
 * response, để tính thời gian còn lại không phụ thuộc giờ máy học sinh.
 */
export type Attempt = {
    id: number;
    sessionId: number;
    title: string;
    status: AttemptStatus;
    startedAt: string;
    deadline: string;
    submittedAt: string | null;
    serverTime: string;
    answeredCount: number;
    totalQuestions: number;
    maxScore: number;
    correctCount: number | null;
    score: number | null;
    answersRevealed: boolean;
    questions: AttemptQuestion[];
};

/** Bài làm kèm lúc trình duyệt nhận được, để so với `serverTime`. */
export type ReceivedAttempt = Attempt & { receivedAt: number };

export async function getAttempt(id: number): Promise<ReceivedAttempt> {
    const attempt = await http.get<Attempt>(`/api/exam/attempts/${id}`);
    return { ...attempt, receivedAt: Date.now() };
}

/** Lưu hoặc đổi đáp án một câu. Bài đã kết thúc báo `ATTEMPT_ALREADY_SUBMITTED`, `ATTEMPT_EXPIRED` hoặc `ATTEMPT_LOCKED`. */
export function saveAnswer(attemptId: number, questionId: number, selectedOption: number): Promise<void> {
    return http.put(`/api/exam/attempts/${attemptId}/answers/${questionId}`, { selectedOption });
}

export function submitAttempt(attemptId: number): Promise<Attempt> {
    return http.post(`/api/exam/attempts/${attemptId}/submit`);
}

export type BrowserEvent = 'TAB_HIDDEN' | 'TAB_VISIBLE' | 'FULLSCREEN_EXIT';

/** Báo cho giáo viên; bài đã kết thúc thì backend bỏ qua. */
export function reportEvent(attemptId: number, type: BrowserEvent): Promise<void> {
    return http.post(`/api/proctor/attempts/${attemptId}/events`, { type, occurredAt: new Date().toISOString() });
}

/** Các mã lỗi cho biết bài đã kết thúc; gặp thì tải lại để chuyển sang màn kết quả. */
export const FINISHED_CODES = ['ATTEMPT_ALREADY_SUBMITTED', 'ATTEMPT_EXPIRED', 'ATTEMPT_LOCKED'];
