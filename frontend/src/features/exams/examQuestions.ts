/** Số câu tối đa của một đề (README 3.2). */
export const MAX_QUESTIONS = 100;

/** Một câu trong đề đang soạn, đủ để hiển thị. */
export type DraftQuestion = {
    id: number;
    content: string;
    options: string[];
    correctOption: number;
};

/** Thêm vào cuối đề. Câu đã có, hoặc đề đã đủ câu, thì giữ nguyên. */
export function addQuestion(questions: DraftQuestion[], question: DraftQuestion): DraftQuestion[] {
    if (questions.length >= MAX_QUESTIONS || questions.some((q) => q.id === question.id)) {
        return questions;
    }
    return [...questions, question];
}

export function removeQuestion(questions: DraftQuestion[], id: number): DraftQuestion[] {
    return questions.filter((q) => q.id !== id);
}

/** Đổi chỗ câu ở `index` với câu kề trên (`-1`) hoặc kề dưới (`1`). Ra ngoài danh sách thì giữ nguyên. */
export function moveQuestion(questions: DraftQuestion[], index: number, direction: -1 | 1): DraftQuestion[] {
    const target = index + direction;
    if (index < 0 || index >= questions.length || target < 0 || target >= questions.length) {
        return questions;
    }
    const moved = [...questions];
    [moved[index], moved[target]] = [moved[target]!, moved[index]!];
    return moved;
}

/** Hai danh sách có cùng câu theo cùng thứ tự không; dùng để biết đề có thay đổi chưa lưu. */
export function sameOrder(a: number[], b: number[]): boolean {
    return a.length === b.length && a.every((id, i) => id === b[i]);
}
