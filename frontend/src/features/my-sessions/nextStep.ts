import type { MySession } from './api';

/** Việc học sinh làm tiếp với một kỳ thi; `to` rỗng là không có nút. */
export type NextStep = { label: string; to: string | null; primary: boolean; note: string | null };

/**
 * Nút hiện ở danh sách "Kỳ thi của tôi". Bắt đầu làm bài luôn đi qua phòng chờ để học sinh đọc luật thi
 * trước khi đồng hồ chạy.
 */
export function nextStep(session: MySession): NextStep {
    const room = `/student/sessions/${session.id}`;
    if (session.attemptId !== null) {
        const attempt = `/student/attempts/${session.attemptId}`;
        return session.attemptStatus === 'IN_PROGRESS'
            ? { label: 'Tiếp tục làm bài', to: attempt, primary: true, note: null }
            : { label: 'Xem bài làm', to: attempt, primary: false, note: null };
    }
    switch (session.status) {
        case 'SCHEDULED':
            return { label: 'Vào phòng chờ', to: room, primary: false, note: null };
        case 'OPEN':
            return { label: 'Vào làm bài', to: room, primary: true, note: 'Kỳ thi đang mở.' };
        case 'CLOSED':
            return { label: '', to: null, primary: false, note: 'Bạn không làm bài trong kỳ thi này.' };
        case 'CANCELLED':
            return { label: '', to: null, primary: false, note: 'Giáo viên đã hủy kỳ thi.' };
    }
}
