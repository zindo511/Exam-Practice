import { http } from '../../shared/http';
import type { PageResponse } from '../../shared/page';
import type { AttemptStatus, ResultVisibility, SessionStatus } from '../sessions/api';

/**
 * Kỳ thi học sinh đã tham gia. Không có mã tham gia và ID đề, vì đó là thông tin của giáo viên.
 * `durationMinutes` rỗng tới khi mở; `correctCount` và `score` chỉ có khi bài đã kết thúc và kỳ thi không ẩn điểm.
 */
export type MySession = {
    id: number;
    title: string;
    status: SessionStatus;
    startAt: string;
    endAt: string;
    durationMinutes: number | null;
    resultVisibility: ResultVisibility;
    maxScore: number;
    attemptId: number | null;
    attemptStatus: AttemptStatus | null;
    correctCount: number | null;
    score: number | null;
};

/** Phần bài làm màn này cần; đủ trường có ở màn làm bài. */
export type StartedAttempt = { id: number; sessionId: number };

export const PAGE_SIZE = 20;

/**
 * Nhập mã để vào phòng chờ; nhập lại mã của kỳ thi đã tham gia vẫn trả về kỳ thi đó. Mã sai, kỳ thi đã
 * khóa hoặc đã kết thúc báo `INVALID_ACCESS_CODE`; sai quá 5 lần trong 15 phút báo `ACCESS_CODE_ATTEMPTS_EXCEEDED`.
 */
export function joinSession(accessCode: string): Promise<MySession> {
    return http.post('/api/exam/sessions/join', { accessCode });
}

/** Giờ bắt đầu muộn nhất trước. */
export function listMySessions(page: number): Promise<PageResponse<MySession>> {
    return http.get(`/api/exam/my-sessions?page=${page}&size=${PAGE_SIZE}`);
}

export function getMySession(id: number): Promise<MySession> {
    return http.get(`/api/exam/my-sessions/${id}`);
}

/** Mỗi học sinh một lượt; đồng hồ bắt đầu chạy ngay. Kỳ thi chưa mở báo `SESSION_NOT_OPEN`. */
export function startAttempt(sessionId: number): Promise<StartedAttempt> {
    return http.post(`/api/exam/sessions/${sessionId}/attempts`);
}
