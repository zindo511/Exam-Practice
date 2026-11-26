import { expect, test } from 'vitest';
import { addQuestion, MAX_QUESTIONS, moveQuestion, removeQuestion, sameOrder, type DraftQuestion } from './examQuestions';

function question(id: number): DraftQuestion {
    return { id, content: `Câu ${id}`, options: ['A', 'B'], correctOption: 0 };
}

const ids = (questions: DraftQuestion[]) => questions.map((q) => q.id);

test('thêm vào cuối đề', () => {
    expect(ids(addQuestion([question(1), question(2)], question(3)))).toEqual([1, 2, 3]);
});

test('không thêm câu đã có', () => {
    const questions = [question(1), question(2)];

    expect(addQuestion(questions, question(1))).toBe(questions);
});

test('không thêm khi đề đã đủ câu', () => {
    const full = Array.from({ length: MAX_QUESTIONS }, (_, i) => question(i + 1));

    expect(addQuestion(full, question(999))).toBe(full);
});

test('bỏ đúng câu được chọn', () => {
    expect(ids(removeQuestion([question(1), question(2), question(3)], 2))).toEqual([1, 3]);
});

test('đưa câu lên và xuống', () => {
    const questions = [question(1), question(2), question(3)];

    expect(ids(moveQuestion(questions, 1, -1))).toEqual([2, 1, 3]);
    expect(ids(moveQuestion(questions, 1, 1))).toEqual([1, 3, 2]);
    expect(ids(questions)).toEqual([1, 2, 3]);
});

test('câu đầu không lên được, câu cuối không xuống được', () => {
    const questions = [question(1), question(2)];

    expect(moveQuestion(questions, 0, -1)).toBe(questions);
    expect(moveQuestion(questions, 1, 1)).toBe(questions);
    expect(moveQuestion([], 0, 1)).toEqual([]);
});

test('so thứ tự câu', () => {
    expect(sameOrder([1, 2, 3], [1, 2, 3])).toBe(true);
    expect(sameOrder([1, 2, 3], [1, 3, 2])).toBe(false);
    expect(sameOrder([1, 2], [1, 2, 3])).toBe(false);
});
