import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { getMySession } from '../my-sessions/api';
import { formatDateTime, formatScore } from '../sessions/format';
import type { Attempt, AttemptQuestion } from './api';
import styles from './Attempt.module.css';

const LETTERS = 'ABCDEF';

function choice(question: AttemptQuestion, option: number | null): string {
    return option === null ? 'Bỏ trống' : `${LETTERS[option]}. ${question.options[option]}`;
}

/** Bài kết thúc bằng cách nào; cả ba cách đều đã được chấm. */
function finishedLine(attempt: Attempt): string {
    const at = attempt.submittedAt === null ? '' : ` lúc ${formatDateTime(attempt.submittedAt)}`;
    switch (attempt.status) {
        case 'EXPIRED':
            return `Hết giờ, bài được tự nộp${at}`;
        case 'LOCKED':
            return `Bài bị giáo viên khóa${at}`;
        default:
            return `Nộp${at}`;
    }
}

/** Điểm và đáp án hiện tùy cài đặt của kỳ thi; backend đã ẩn những gì học sinh chưa được xem. */
export function AttemptResult({ attempt }: { attempt: Attempt }) {
    // Chỉ để chọn câu thông báo khi chưa có đáp án; lỗi thì bỏ qua câu đó.
    const session = useQuery({
        queryKey: ['my-session', attempt.sessionId],
        queryFn: () => getMySession(attempt.sessionId),
    });
    const visibility = session.data?.resultVisibility;

    const wrong = attempt.questions.filter((q) => q.correct !== true);
    const noAnswersText = visibility === 'HIDDEN'
        ? 'Bài của bạn đã được nộp và chấm. Điểm sẽ hiện ở đây khi giáo viên cho xem.'
        : visibility === 'SCORE'
            ? 'Kỳ thi này không cho xem đáp án.'
            : visibility === 'SCORE_AND_ANSWERS'
                ? 'Đáp án sẽ hiện ở đây sau khi kỳ thi kết thúc.'
                : null;

    return (
        <div className={styles.resultPage}>
            <Link to="/student">Kỳ thi của tôi</Link>

            <section className={styles.resultHero}>
                <div className={styles.resultHeading}>
                    <h1 className={styles.resultTitle}>{attempt.title}</h1>
                    <div className={styles.resultLine}>{finishedLine(attempt)}</div>
                </div>
                {attempt.score === null ? (
                    <div className={styles.resultHidden}>Giáo viên chưa cho xem điểm</div>
                ) : (
                    <div className={styles.scoreBox}>
                        <div className={styles.bigScore}>{formatScore(attempt.score)}</div>
                        <div className={styles.scoreNote}>
                            trên {attempt.maxScore}
                            <br />
                            Đúng {attempt.correctCount}/{attempt.totalQuestions} câu
                        </div>
                    </div>
                )}
            </section>

            {attempt.answersRevealed ? (
                <div className={styles.resultColumns}>
                    <section className={styles.wrongPanel} aria-labelledby="wrong-title">
                        <h2 id="wrong-title" className={styles.panelTitle}>Câu chưa đúng</h2>
                        {wrong.length === 0 ? (
                            <p className={styles.muted}>Bạn trả lời đúng tất cả các câu.</p>
                        ) : (
                            <ol className={styles.wrongList}>
                                {wrong.map((q) => (
                                    <li key={q.questionId} className={styles.wrongItem}>
                                        <div className={styles.wrongContent}>
                                            <span className={styles.wrongNumber}>Câu {q.position}. </span>{q.content}
                                        </div>
                                        <dl className={styles.wrongFacts}>
                                            <dt>Bạn chọn</dt>
                                            <dd className={styles.picked}>{choice(q, q.selectedOption)}</dd>
                                            <dt>Đáp án đúng</dt>
                                            <dd>{choice(q, q.correctOption)}</dd>
                                        </dl>
                                    </li>
                                ))}
                            </ol>
                        )}
                    </section>
                    <aside className={styles.palettePanel}>
                        <h2 className={styles.paletteTitle}>Tất cả {attempt.totalQuestions} câu</h2>
                        <div className={styles.palette}>
                            {attempt.questions.map((q) => (
                                <div
                                    key={q.questionId}
                                    role="img"
                                    aria-label={`Câu ${q.position}: ${q.correct === true ? 'đúng' : 'chưa đúng'}`}
                                    className={q.correct === true ? styles.cell : styles.cellWrong}
                                >
                                    {q.position}
                                </div>
                            ))}
                        </div>
                        <div className={styles.legend}>
                            <div><span aria-hidden="true" className={styles.swatch} />Đúng</div>
                            <div><span aria-hidden="true" className={styles.swatchWrong} />Sai hoặc bỏ trống</div>
                        </div>
                    </aside>
                </div>
            ) : noAnswersText !== null && (
                <section className={styles.noteBox}>{noAnswersText}</section>
            )}
        </div>
    );
}
