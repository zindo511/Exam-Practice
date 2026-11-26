import { http } from '../../shared/http';
import type { PageResponse } from '../../shared/page';

/** Trạng thái kỳ thi (docs/state-machines.md mục 1). */
export type SessionStatus = 'SCHEDULED' | 'OPEN' | 'CLOSED' | 'CANCELLED';

/** Học sinh được xem gì sau khi bài kết thúc; giáo viên luôn thấy đầy đủ. */
export type ResultVisibility = 'HIDDEN' | 'SCORE' | 'SCORE_AND_ANSWERS';

/** Trạng thái bài làm (docs/state-machines.md mục 2). Ba trạng thái sau `IN_PROGRESS` đều đã có điểm. */
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'EXPIRED' | 'LOCKED';

/** Lọc bảng điểm; `NOT_STARTED` là đã vào phòng chờ nhưng chưa làm bài. */
export type ResultFilter = 'NOT_STARTED' | AttemptStatus;

/**
 * Kỳ thi, dùng cho cả danh sách và chi tiết. Thời điểm là chuỗi ISO-8601 UTC.
 * `durationMinutes` rỗng tới khi mở; `examUsable` chỉ có ở kỳ thi `SCHEDULED`.
 */
export type Session = {
    id: number;
    examId: number;
    title: string;
    accessCode: string;
    joinOpen: boolean;
    status: SessionStatus;
    resultVisibility: ResultVisibility;
    maxScore: number;
    startAt: string;
    endAt: string;
    durationMinutes: number | null;
    openedAt: string | null;
    closedAt: string | null;
    createdAt: string;
    examUsable: boolean | null;
};

export type SessionInput = {
    examId: number;
    startAt: string;
    endAt: string;
    resultVisibility: ResultVisibility;
    maxScore: number;
};

export type ResultSummary = {
    participantCount: number;
    notStartedCount: number;
    inProgressCount: number;
    finishedCount: number;
    averageScore: number | null;
    highestScore: number | null;
    lowestScore: number | null;
};

/** Một học sinh đã nhập mã; các field bài làm rỗng nếu chưa bắt đầu, điểm rỗng khi đang làm. */
export type StudentResultRow = {
    studentId: number;
    username: string | null;
    fullName: string | null;
    joinedAt: string;
    attemptId: number | null;
    attemptStatus: AttemptStatus | null;
    startedAt: string | null;
    submittedAt: string | null;
    correctCount: number | null;
    score: number | null;
};

export type SessionResults = {
    sessionId: number;
    title: string;
    status: SessionStatus;
    maxScore: number;
    /** 0 nếu kỳ thi chưa mở. */
    totalQuestions: number;
    /** Tính trên cả kỳ thi, không chỉ trang hiện tại. */
    summary: ResultSummary;
    students: PageResponse<StudentResultRow>;
};

export type AttemptItem = {
    questionId: number;
    position: number;
    content: string;
    options: string[];
    /** Rỗng nếu học sinh bỏ trống. */
    selectedOption: number | null;
    correctOption: number;
    correct: boolean;
};

export type TeacherAttempt = {
    attemptId: number;
    sessionId: number;
    studentId: number;
    username: string | null;
    fullName: string | null;
    status: AttemptStatus;
    startedAt: string;
    deadline: string;
    submittedAt: string | null;
    maxScore: number;
    correctCount: number | null;
    score: number | null;
    answeredCount: number;
    totalQuestions: number;
    questions: AttemptItem[];
};

export const PAGE_SIZE = 20;

/** Giờ bắt đầu muộn nhất trước. `status` rỗng thì lấy tất cả. */
export function listSessions(page: number, status: SessionStatus | null): Promise<PageResponse<Session>> {
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (status !== null) {
        params.set('status', status);
    }
    return http.get(`/api/exam/sessions?${params}`);
}

export function getSession(id: number): Promise<Session> {
    return http.get(`/api/exam/sessions/${id}`);
}

/** Đề không dùng được (đã xóa, chưa có câu) báo `EXAM_NOT_AVAILABLE`. */
export function createSession(input: SessionInput): Promise<Session> {
    return http.post('/api/exam/sessions', input);
}

/** Mở sớm và chụp đề; sau đó sửa đề không ảnh hưởng kỳ thi. */
export function openSession(id: number): Promise<Session> {
    return http.post(`/api/exam/sessions/${id}/open`);
}

/** Kết thúc sớm; bài đang làm được chốt và chấm từ đáp án đã lưu. */
export function closeSession(id: number): Promise<Session> {
    return http.post(`/api/exam/sessions/${id}/close`);
}

export function cancelSession(id: number): Promise<Session> {
    return http.post(`/api/exam/sessions/${id}/cancel`);
}

export function setJoinOpen(id: number, open: boolean): Promise<Session> {
    return http.post(`/api/exam/sessions/${id}/${open ? 'unlock-join' : 'lock-join'}`);
}

export function changeResultVisibility(id: number, resultVisibility: ResultVisibility): Promise<Session> {
    return http.put(`/api/exam/sessions/${id}/result-visibility`, { resultVisibility });
}

/** Điểm cao trước, chưa có điểm xuống cuối. */
export function getResults(id: number, page: number, status: ResultFilter | null): Promise<SessionResults> {
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (status !== null) {
        params.set('status', status);
    }
    return http.get(`/api/exam/sessions/${id}/results?${params}`);
}

export function getAttempt(sessionId: number, attemptId: number): Promise<TeacherAttempt> {
    return http.get(`/api/exam/sessions/${sessionId}/attempts/${attemptId}`);
}
