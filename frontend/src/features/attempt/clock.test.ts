import { expect, test } from 'vitest';
import { formatClock, remainingMs } from './clock';

const deadline = '2026-11-26T10:30:00Z';

test('thời gian còn lại theo giờ server, kể cả khi giờ máy lệch', () => {
    // Server đang 10:00:00; máy học sinh chạy nhanh 5 phút nên lúc nhận đọc ra 10:05:00.
    const receivedAt = Date.parse('2026-11-26T10:05:00Z');
    expect(remainingMs(deadline, '2026-11-26T10:00:00Z', receivedAt, receivedAt)).toBe(30 * 60_000);
    expect(remainingMs(deadline, '2026-11-26T10:00:00Z', receivedAt, receivedAt + 60_000)).toBe(29 * 60_000);
});

test('quá hạn chót thì còn 0, không âm', () => {
    const receivedAt = Date.parse('2026-11-26T10:31:00Z');
    expect(remainingMs(deadline, '2026-11-26T10:31:00Z', receivedAt, receivedAt)).toBe(0);
});

test('định dạng đồng hồ', () => {
    expect(formatClock(32 * 60_000 + 18_000)).toBe('32:18');
    expect(formatClock(65 * 60_000 + 9_000)).toBe('1:05:09');
    expect(formatClock(0)).toBe('00:00');
});

test('làm tròn lên để không hiện 00:00 khi còn chưa tới một giây', () => {
    expect(formatClock(400)).toBe('00:01');
});
