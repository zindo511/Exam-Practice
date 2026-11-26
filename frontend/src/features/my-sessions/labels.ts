import type { AttemptStatus } from '../sessions/api';

/** Nhãn theo cách học sinh hiểu; khác nhãn của giáo viên ("Tự nộp", "Bị khóa"). */
export const ATTEMPT_LABEL: Record<AttemptStatus, string> = {
    IN_PROGRESS: 'Đang làm bài',
    SUBMITTED: 'Đã nộp bài',
    EXPIRED: 'Hết giờ, bài đã được tự nộp',
    LOCKED: 'Bài bị giáo viên khóa',
};
