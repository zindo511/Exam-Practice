import { expect, test } from 'vitest';
import { formatScore, fromLocalInput, suggestedStart, toLocalInput } from './format';

test('giờ gợi ý làm tròn lên mốc 15 phút, cách hiện tại ít nhất 5 phút', () => {
    expect(toLocalInput(suggestedStart(new Date(2026, 10, 26, 14, 2, 30)))).toBe('2026-11-26T14:15');
    expect(toLocalInput(suggestedStart(new Date(2026, 10, 26, 14, 12)))).toBe('2026-11-26T14:30');
    expect(toLocalInput(suggestedStart(new Date(2026, 10, 26, 14, 10)))).toBe('2026-11-26T14:15');
});

test('giờ gợi ý sang ngày sau khi gần nửa đêm', () => {
    expect(toLocalInput(suggestedStart(new Date(2026, 10, 26, 23, 58)))).toBe('2026-11-27T00:15');
});

test('đọc ô giờ theo giờ máy và gửi dạng UTC', () => {
    expect(fromLocalInput('2026-11-26T14:30')).toBe(new Date(2026, 10, 26, 14, 30).toISOString());
});

test('ô giờ rỗng hoặc sai thì không có giá trị', () => {
    expect(fromLocalInput('')).toBeNull();
    expect(fromLocalInput('abc')).toBeNull();
});

test('điểm bỏ số 0 thừa, dùng dấu phẩy', () => {
    expect(formatScore(8.5)).toBe('8,5');
    expect(formatScore(10)).toBe('10');
    expect(formatScore(7.25)).toBe('7,25');
});
