import { expect, test } from 'vitest';
import type { MySession } from './api';
import { nextStep } from './nextStep';

function session(overrides: Partial<MySession>): MySession {
    return {
        id: 7,
        title: 'Kiểm tra 15 phút',
        status: 'SCHEDULED',
        startAt: '2026-11-26T08:00:00Z',
        endAt: '2026-11-26T09:00:00Z',
        durationMinutes: null,
        resultVisibility: 'SCORE',
        maxScore: 10,
        attemptId: null,
        attemptStatus: null,
        correctCount: null,
        score: null,
        ...overrides,
    };
}

test('chưa mở thì vào phòng chờ', () => {
    expect(nextStep(session({}))).toMatchObject({ label: 'Vào phòng chờ', to: '/student/sessions/7' });
});

test('đang mở mà chưa làm thì vẫn qua phòng chờ để đọc luật thi', () => {
    expect(nextStep(session({ status: 'OPEN' }))).toMatchObject({ to: '/student/sessions/7', primary: true });
});

test('đang làm thì tiếp tục bài làm', () => {
    expect(nextStep(session({ status: 'OPEN', attemptId: 3, attemptStatus: 'IN_PROGRESS' })))
        .toMatchObject({ label: 'Tiếp tục làm bài', to: '/student/attempts/3' });
});

test('bài đã kết thúc thì xem bài, kể cả khi kỳ thi đã đóng', () => {
    expect(nextStep(session({ status: 'CLOSED', attemptId: 3, attemptStatus: 'EXPIRED' })))
        .toMatchObject({ label: 'Xem bài làm', to: '/student/attempts/3' });
});

test('kỳ thi đã đóng hoặc bị hủy mà chưa làm thì không có nút', () => {
    expect(nextStep(session({ status: 'CLOSED' })).to).toBeNull();
    expect(nextStep(session({ status: 'CANCELLED' })).to).toBeNull();
});
