import { useEffect, useRef, useState } from 'react';
import { Button, Modal } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../shared/http';
import { FINISHED_CODES, reportEvent, saveAnswer, submitAttempt, type ReceivedAttempt } from './api';
import styles from './Attempt.module.css';
import { formatClock, formatTimeOfDay, remainingMs } from './clock';

const LETTERS = 'ABCDEF';

/** Còn dưới 5 phút thì đồng hồ đổi màu cam. */
const WARN_MS = 5 * 60_000;

type SaveState =
    | { kind: 'idle' }
    | { kind: 'saving' }
    | { kind: 'saved'; at: Date }
    | { kind: 'error'; position: number };

/**
 * Màn làm bài: một câu mỗi lần, chọn là lưu ngay. `attempt` được tải lại định kỳ để cập nhật hạn chót;
 * đáp án trên màn hình là của học sinh, không bị ghi đè khi tải lại.
 */
export function TakeExam({ attempt }: { attempt: ReceivedAttempt }) {
    const queryClient = useQueryClient();
    const questions = attempt.questions;
    const total = questions.length;
    const [current, setCurrent] = useState(0);
    const [answers, setAnswers] = useState<Record<number, number>>(() => Object.fromEntries(
        questions.filter((q) => q.selectedOption !== null).map((q) => [q.questionId, q.selectedOption!]),
    ));
    const [save, setSave] = useState<SaveState>({ kind: 'idle' });
    const [warning, setWarning] = useState(false);
    const [confirming, setConfirming] = useState(false);
    const [now, setNow] = useState(() => Date.now());

    const reloadAttempt = () => void queryClient.invalidateQueries({ queryKey: ['attempt', attempt.id] });

    // Lưu lần lượt, để lần chọn sau không bị lần chọn trước ghi đè khi request về không theo thứ tự.
    const saves = useRef<Promise<void>>(Promise.resolve());

    function pick(questionId: number, position: number, option: number): void {
        setAnswers((a) => ({ ...a, [questionId]: option }));
        setSave({ kind: 'saving' });
        saves.current = saves.current.then(() => saveAnswer(attempt.id, questionId, option).then(
            () => setSave({ kind: 'saved', at: new Date() }),
            (error: unknown) => {
                if (error instanceof ApiError && FINISHED_CODES.includes(error.code)) {
                    reloadAttempt();
                } else {
                    setSave({ kind: 'error', position });
                }
            },
        ));
    }

    const submit = useMutation({
        mutationFn: async () => {
            await saves.current;
            return submitAttempt(attempt.id);
        },
        onSuccess: (result) => {
            queryClient.setQueryData(['attempt', attempt.id], { ...result, receivedAt: Date.now() });
            void queryClient.invalidateQueries({ queryKey: ['my-sessions'] });
            void queryClient.invalidateQueries({ queryKey: ['my-session', attempt.sessionId] });
        },
        onError: (error) => {
            if (error instanceof ApiError && FINISHED_CODES.includes(error.code)) {
                reloadAttempt();
            }
        },
    });

    const remaining = remainingMs(attempt.deadline, attempt.serverTime, attempt.receivedAt, now);

    useEffect(() => {
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, []);

    // Hết giờ thì tự nộp một lần; nếu server đã chốt bài trước thì lỗi trả về sẽ chuyển sang màn kết quả.
    const autoSubmitted = useRef(false);
    useEffect(() => {
        if (remaining === 0 && !autoSubmitted.current) {
            autoSubmitted.current = true;
            setConfirming(false);
            submit.mutate();
        }
    }, [remaining, submit]);

    // Rời tab thì báo giáo viên và nhắc học sinh. Gửi lỗi thì thôi, không làm phiền học sinh.
    useEffect(() => {
        function onVisibility(): void {
            const hidden = document.visibilityState === 'hidden';
            reportEvent(attempt.id, hidden ? 'TAB_HIDDEN' : 'TAB_VISIBLE').catch(() => undefined);
            if (hidden) {
                setWarning(true);
            }
        }
        document.addEventListener('visibilitychange', onVisibility);
        return () => document.removeEventListener('visibilitychange', onVisibility);
    }, [attempt.id]);

    const question = questions[current]!;
    const done = questions.filter((q) => answers[q.questionId] !== undefined).length;
    const left = total - done;

    const timeUp = remaining === 0;
    const saveText = timeUp ? 'Hết giờ, đang chốt bài…'
        : save.kind === 'saving' ? 'Đang lưu…'
        : save.kind === 'saved' ? `Đã lưu lúc ${formatTimeOfDay(save.at)}`
            : save.kind === 'error' ? `Chưa lưu được câu ${save.position}. Kiểm tra mạng rồi chọn lại đáp án.`
                : 'Đáp án được lưu ngay khi bạn chọn.';

    return (
        <div className={styles.examPage}>
            <header className={styles.examHeader}>
                <div className={styles.examHeaderInner}>
                    <div className={styles.examTitleBox}>
                        <h1 className={styles.examTitle}>{attempt.title}</h1>
                        <div role="status" className={save.kind === 'error' ? styles.saveError : styles.muted}>{saveText}</div>
                    </div>
                    <div className={styles.timer}>
                        <span className={styles.muted}>Còn lại</span>
                        <span
                            className={remaining < WARN_MS ? styles.timeWarn : styles.time}
                            aria-label={`Còn lại ${formatClock(remaining)}`}
                        >
                            {formatClock(remaining)}
                        </span>
                    </div>
                    <Button type="primary" size="large" className={styles.submitButton} loading={submit.isPending} disabled={timeUp} onClick={() => setConfirming(true)}>
                        Nộp bài
                    </Button>
                </div>
            </header>

            {warning && (
                <div role="alert" className={styles.warning}>
                    <div className={styles.warningInner}>
                        <div><strong>Bạn vừa rời màn hình làm bài.</strong> Giáo viên nhìn thấy việc này. Hãy ở lại trang này tới khi nộp bài.</div>
                        <button type="button" className={styles.warningButton} onClick={() => setWarning(false)}>Đã hiểu</button>
                    </div>
                </div>
            )}

            <div className={styles.examBody}>
                <main className={styles.questionColumn}>
                    <div className={styles.questionCard}>
                        <div className={styles.questionHead}>
                            <div className={styles.questionNumber}>Câu {current + 1} trên {total}</div>
                            <p id="question" className={styles.questionContent}>{question.content}</p>
                        </div>
                        <div role="radiogroup" aria-labelledby="question" className={styles.options}>
                            {question.options.map((option, index) => {
                                const checked = answers[question.questionId] === index;
                                return (
                                    <label key={index} className={checked ? styles.optionChecked : styles.option}>
                                        <input
                                            type="radio"
                                            name={`answer-${question.questionId}`}
                                            checked={checked}
                                            disabled={timeUp}
                                            onChange={() => pick(question.questionId, current + 1, index)}
                                        />
                                        <span className={styles.optionLetter}>{LETTERS[index]}</span>
                                        <span className={styles.optionText}>{option}</span>
                                    </label>
                                );
                            })}
                        </div>
                    </div>
                    <div className={styles.navButtons}>
                        <Button size="large" disabled={current === 0} onClick={() => setCurrent(current - 1)}>Câu trước</Button>
                        <Button size="large" disabled={current === total - 1} onClick={() => setCurrent(current + 1)}>Câu tiếp</Button>
                    </div>
                </main>

                <aside className={styles.palettePanel}>
                    <div className={styles.paletteHead}>
                        <h2 className={styles.paletteTitle}>Danh sách câu</h2>
                        <div>Đã làm <strong>{done}/{total}</strong></div>
                    </div>
                    <nav aria-label="Danh sách câu" className={styles.palette}>
                        {questions.map((q, index) => {
                            const answered = answers[q.questionId] !== undefined;
                            const here = index === current;
                            const cls = [answered ? styles.cellAnswered : styles.cell, here ? styles.cellCurrent : ''].join(' ');
                            return (
                                <button
                                    key={q.questionId}
                                    type="button"
                                    className={cls}
                                    aria-label={`Câu ${index + 1}, ${answered ? 'đã trả lời' : 'chưa trả lời'}`}
                                    aria-current={here ? 'true' : undefined}
                                    onClick={() => setCurrent(index)}
                                >
                                    {index + 1}
                                </button>
                            );
                        })}
                    </nav>
                    <div className={styles.legend}>
                        <div><span aria-hidden="true" className={styles.swatchAnswered} />Đã trả lời</div>
                        <div><span aria-hidden="true" className={styles.swatch} />Chưa trả lời</div>
                        <div><span aria-hidden="true" className={styles.swatchCurrent} />Câu đang xem</div>
                    </div>
                </aside>
            </div>

            <Modal
                open={confirming}
                title={<span className={styles.dialogTitle}>Nộp bài?</span>}
                onCancel={() => setConfirming(false)}
                footer={(
                    <div className={styles.dialogButtons}>
                        <Button size="large" onClick={() => setConfirming(false)}>Làm tiếp</Button>
                        <Button type="primary" size="large" loading={submit.isPending} onClick={() => submit.mutate()}>Nộp bài</Button>
                    </div>
                )}
            >
                <p className={styles.dialogLead}>
                    {left === 0 ? `Bạn đã trả lời hết ${total} câu.` : `Bạn còn ${left} câu chưa trả lời.`}
                </p>
                <p className={styles.muted}>Nộp rồi thì không sửa đáp án được nữa.</p>
                {submit.error !== null && <p className={styles.saveError}>{submit.error.message}</p>}
            </Modal>
        </div>
    );
}
