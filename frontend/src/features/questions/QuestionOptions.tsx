import styles from './QuestionOptions.module.css';

const LETTERS = 'ABCDEF';

/** Các lựa chọn A, B, C… của một câu hỏi; đáp án đúng được tô màu và gạch chân. */
export function QuestionOptions({ options, correctOption }: { options: string[]; correctOption: number }) {
    return (
        <div className={styles.options}>
            {options.map((option, index) => (
                <span key={index} className={index === correctOption ? styles.correct : styles.option}>
                    {LETTERS[index]}. {option}
                    {index === correctOption && <span className={styles.srOnly}> (đáp án đúng)</span>}
                </span>
            ))}
        </div>
    );
}
