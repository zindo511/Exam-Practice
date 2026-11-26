import type { AttemptStatus, ResultVisibility, SessionStatus } from './api';

export const SESSION_STATUS_LABEL: Record<SessionStatus, string> = {
    SCHEDULED: 'Đã lên lịch',
    OPEN: 'Đang thi',
    CLOSED: 'Đã kết thúc',
    CANCELLED: 'Đã hủy',
};

export const ATTEMPT_STATUS_LABEL: Record<AttemptStatus, string> = {
    IN_PROGRESS: 'Đang làm',
    SUBMITTED: 'Tự nộp',
    EXPIRED: 'Hết giờ',
    LOCKED: 'Bị khóa',
};

export const VISIBILITY_OPTIONS: { value: ResultVisibility; label: string }[] = [
    { value: 'HIDDEN', label: 'Không thấy điểm' },
    { value: 'SCORE', label: 'Thấy điểm ngay khi nộp bài' },
    { value: 'SCORE_AND_ANSWERS', label: 'Thấy điểm, và đáp án sau khi kỳ thi kết thúc' },
];

const DATE_TIME = new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
});

/** Giờ địa phương, ví dụ "14:30 26/11/2026". */
export function formatDateTime(iso: string): string {
    return DATE_TIME.format(new Date(iso));
}

/** Điểm theo kiểu Việt Nam, bỏ số 0 thừa: 8.5 → "8,5". */
export function formatScore(score: number): string {
    return score.toLocaleString('vi-VN', { maximumFractionDigits: 2 });
}

const pad = (n: number) => String(n).padStart(2, '0');

/** Giờ địa phương theo dạng ô `datetime-local` cần: "2026-11-26T14:30". */
export function toLocalInput(date: Date): string {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Ô `datetime-local` không có múi giờ, nên đọc theo giờ máy rồi gửi lên dạng UTC. Rỗng hoặc sai thì `null`. */
export function fromLocalInput(value: string): string | null {
    if (value === '') {
        return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** Giờ mở gợi ý: mốc 15 phút kế tiếp, cách hiện tại ít nhất 5 phút để kịp gửi mã cho học sinh. */
export function suggestedStart(now: Date): Date {
    const start = new Date(now.getTime() + 5 * 60_000);
    start.setSeconds(0, 0);
    const minutes = start.getMinutes();
    if (minutes % 15 !== 0) {
        start.setMinutes(minutes + 15 - (minutes % 15));
    }
    return start;
}
