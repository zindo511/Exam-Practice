import { Alert, Button, Modal, Skeleton } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getAttempt, type AttemptItem } from './api';
import { ATTEMPT_STATUS_LABEL, formatDateTime, formatScore } from './format';
import styles from './Sessions.module.css';

const LETTERS = 'ABCDEF';

type Props = { sessionId: number; attemptId: number; onClose: () => void };

/** Bài làm của một học sinh: từng câu chọn gì, đáp án đúng là gì. */
export function AttemptDialog({ sessionId, attemptId, onClose }: Props) {
    const attempt = useQuery({
        queryKey: ['attempt', sessionId, attemptId],
        queryFn: () => getAttempt(sessionId, attemptId),
    });
    const data = attempt.data;

    return (
        <Modal
            open
            width={760}
            title={data === undefined ? 'Bài làm' : `Bài làm của ${data.fullName ?? data.username ?? 'học sinh'}`}
            footer={<Button onClick={onClose}>Đóng</Button>}
            onCancel={onClose}
        >
            {attempt.isError && (
                <Alert
                    type="error"
                    showIcon
                    title={attempt.error.message}
                    action={<Button onClick={() => void attempt.refetch()}>Thử lại</Button>}
                />
            )}
            {attempt.isPending && <Skeleton active paragraph={{ rows: 6 }} />}
            {data !== undefined && (
                <div className={styles.attempt}>
                    <dl className={styles.facts}>
                        <dt>Trạng thái</dt>
                        <dd>{ATTEMPT_STATUS_LABEL[data.status]}</dd>
                        <dt>Điểm</dt>
                        <dd>
                            {data.score === null
                                ? 'Chưa có (bài đang làm)'
                                : `${formatScore(data.score)}/${data.maxScore}, đúng ${data.correctCount}/${data.totalQuestions} câu`}
                        </dd>
                        <dt>Đã trả lời</dt>
                        <dd>{data.answeredCount}/{data.totalQuestions} câu</dd>
                        <dt>Bắt đầu</dt>
                        <dd>{formatDateTime(data.startedAt)}</dd>
                        <dt>Hạn chót</dt>
                        <dd>{formatDateTime(data.deadline)}</dd>
                        {data.submittedAt !== null && (
                            <>
                                <dt>Kết thúc</dt>
                                <dd>{formatDateTime(data.submittedAt)}</dd>
                            </>
                        )}
                    </dl>
                    <ol className={styles.answerList}>
                        {data.questions.map((item) => <AnswerRow key={item.questionId} item={item} />)}
                    </ol>
                </div>
            )}
        </Modal>
    );
}

function AnswerRow({ item }: { item: AttemptItem }) {
    const verdict = item.selectedOption === null ? 'Bỏ trống' : item.correct ? 'Đúng' : 'Sai';
    return (
        <li className={styles.answerRow}>
            <div className={styles.answerHead}>
                <span className={styles.number}>{item.position}</span>
                <span className={styles.answerContent}>{item.content}</span>
                <span className={item.correct ? styles.right : styles.wrong}>{verdict}</span>
            </div>
            <ul className={styles.choices}>
                {item.options.map((option, index) => {
                    const chosen = index === item.selectedOption;
                    const correct = index === item.correctOption;
                    return (
                        <li key={index} className={correct ? styles.choiceCorrect : chosen ? styles.choiceWrong : styles.choice}>
                            {LETTERS[index]}. {option}
                            {chosen && <span className={styles.choiceNote}> · học sinh chọn</span>}
                            {correct && <span className={styles.choiceNote}> · đáp án đúng</span>}
                        </li>
                    );
                })}
            </ul>
        </li>
    );
}
