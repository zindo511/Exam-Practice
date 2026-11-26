import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Empty, Flex, Input, InputNumber, Result, Skeleton, Typography } from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useBlocker, useParams } from 'react-router';
import { ApiError } from '../../shared/http';
import { getQuestion } from '../questions/api';
import { QuestionOptions } from '../questions/QuestionOptions';
import { getExam, replaceExamQuestions, updateExam, type ExamDetail } from './api';
import styles from './ExamEditorPage.module.css';
import { addQuestion, MAX_QUESTIONS, moveQuestion, removeQuestion, sameOrder, type DraftQuestion } from './examQuestions';
import { QuestionBankPanel } from './QuestionBankPanel';

export function ExamEditorPage() {
    const id = Number(useParams().examId);
    const exam = useQuery({
        queryKey: ['exam', id],
        queryFn: () => getExam(id),
        enabled: Number.isInteger(id),
        // Đang soạn mà tải lại thì không dùng được dữ liệu mới, nên không tự tải lại.
        staleTime: Infinity,
    });

    const notFound = !Number.isInteger(id) || (exam.error instanceof ApiError && exam.error.code === 'EXAM_NOT_FOUND');
    if (notFound) {
        return (
            <Result
                status="404"
                title="Không tìm thấy đề"
                subTitle="Đề không tồn tại, đã bị xóa, hoặc thuộc giáo viên khác."
                extra={<Link to="/teacher/exams">Về danh sách đề</Link>}
            />
        );
    }
    if (exam.isError) {
        return (
            <Alert
                type="error"
                showIcon
                title={exam.error.message}
                action={<Button onClick={() => void exam.refetch()}>Thử lại</Button>}
            />
        );
    }
    if (exam.data === undefined) {
        return <Skeleton active paragraph={{ rows: 8 }} />;
    }
    return <ExamEditor key={id} exam={exam.data} />;
}

type Saved = { title: string; durationMinutes: number; questionIds: number[] };

/** Thay đổi chỉ nằm trên giao diện cho tới khi bấm "Lưu đề". */
function ExamEditor({ exam }: { exam: ExamDetail }) {
    const { message, modal } = App.useApp();
    const queryClient = useQueryClient();
    const [title, setTitle] = useState(exam.title);
    const [duration, setDuration] = useState<number | null>(exam.durationMinutes);
    const [questions, setQuestions] = useState<DraftQuestion[]>(
        exam.questions.map(({ questionId, content, options, correctOption }) => ({ id: questionId, content, options, correctOption })),
    );
    const [saved, setSaved] = useState<Saved>({
        title: exam.title,
        durationMinutes: exam.durationMinutes,
        questionIds: exam.questions.map((q) => q.questionId),
    });
    /** Câu bị xóa khỏi ngân hàng sau khi được thêm vào đề, để đánh dấu đỏ. */
    const [unavailable, setUnavailable] = useState<Set<number>>(new Set());

    const questionIds = questions.map((q) => q.id);
    const metaChanged = title.trim() !== saved.title || duration !== saved.durationMinutes;
    const questionsChanged = !sameOrder(questionIds, saved.questionIds);
    const dirty = metaChanged || questionsChanged;

    const titleError = title.trim() === '' ? 'Nhập tên đề' : title.trim().length > 200 ? 'Tối đa 200 ký tự' : null;
    const durationError = duration === null ? 'Nhập thời lượng' : null;

    /**
     * Hai API riêng nên lưu từng phần; phần nào xong thì ghi nhận phần đó, bấm Lưu lại là gửi phần còn thiếu.
     * Query của đề không tự tải lại (staleTime: Infinity), nên mỗi phần lưu xong phải ghi vào cache,
     * nếu không rời trang rồi quay lại sẽ thấy bản cũ.
     */
    const save = useMutation({
        mutationFn: async () => {
            const meta = { title: title.trim(), durationMinutes: duration! };
            if (metaChanged) {
                const updated = await updateExam(exam.id, meta);
                setSaved((s) => ({ ...s, ...meta }));
                queryClient.setQueryData<ExamDetail>(['exam', exam.id], (old) => ({ ...(old ?? exam), ...updated }));
            }
            if (questionsChanged) {
                const detail = await replaceExamQuestions(exam.id, questionIds);
                setSaved((s) => ({ ...s, questionIds }));
                queryClient.setQueryData<ExamDetail>(['exam', exam.id], detail);
            }
        },
        onSuccess: () => {
            message.success('Đã lưu đề');
            setUnavailable(new Set());
            void queryClient.invalidateQueries({ queryKey: ['exams'] });
        },
        onError: (error) => {
            if (error instanceof ApiError && error.code === 'QUESTION_NOT_AVAILABLE') {
                void findUnavailable().then(setUnavailable);
                void queryClient.invalidateQueries({ queryKey: ['questions'] });
            }
        },
    });

    /**
     * Câu đã lưu trong đề thì backend không cho xóa (QUESTION_IN_USE), nên chỉ cần hỏi lại các câu vừa thêm.
     * Không đọc ID trong thông báo lỗi, vì thông báo chỉ để hiển thị.
     */
    async function findUnavailable(): Promise<Set<number>> {
        const added = questions.filter((q) => !saved.questionIds.includes(q.id));
        const gone = await Promise.all(added.map((q) => getQuestion(q.id).then(
            () => null,
            (e: unknown) => (e instanceof ApiError && e.code === 'QUESTION_NOT_FOUND' ? q.id : null),
        )));
        return new Set(gone.filter((id): id is number => id !== null));
    }

    // Rời sang trang khác khi chưa lưu thì hỏi lại.
    // Đối tượng blocker đổi sau mỗi lần vẽ lại, nên chỉ chạy lại khi trạng thái đổi; nếu không sẽ mở chồng nhiều hộp thoại.
    const blocker = useBlocker(({ currentLocation, nextLocation }) =>
        dirty && currentLocation.pathname !== nextLocation.pathname);
    const blockerRef = useRef(blocker);
    blockerRef.current = blocker;
    useEffect(() => {
        if (blocker.state !== 'blocked') {
            return;
        }
        modal.confirm({
            title: 'Rời trang khi chưa lưu?',
            content: 'Các thay đổi của đề sẽ mất.',
            okText: 'Rời trang',
            okButtonProps: { danger: true },
            cancelText: 'Ở lại',
            onOk: () => blockerRef.current.proceed?.(),
            onCancel: () => blockerRef.current.reset?.(),
        });
    }, [blocker.state, modal]);

    // Đóng tab hoặc tải lại trang khi chưa lưu thì trình duyệt tự hỏi.
    useEffect(() => {
        if (!dirty) {
            return;
        }
        const warn = (event: BeforeUnloadEvent) => event.preventDefault();
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);

    const full = questions.length >= MAX_QUESTIONS;

    return (
        <Flex vertical gap={20}>
            <Link to="/teacher/exams">Tất cả đề</Link>

            <Flex wrap align="flex-start" gap={16}>
                <div className={`${styles.field} ${styles.titleField}`}>
                    <label htmlFor="exam-title" className={styles.label}>Tên đề</label>
                    <Input
                        id="exam-title"
                        size="large"
                        maxLength={200}
                        value={title}
                        status={titleError === null ? undefined : 'error'}
                        onChange={(e) => setTitle(e.target.value)}
                    />
                    {titleError !== null && <div className={styles.fieldError}>{titleError}</div>}
                </div>
                <div className={styles.field}>
                    <label htmlFor="exam-duration" className={styles.label}>Thời lượng (phút)</label>
                    <InputNumber
                        id="exam-duration"
                        size="large"
                        min={1}
                        max={300}
                        precision={0}
                        value={duration}
                        status={durationError === null ? undefined : 'error'}
                        onChange={setDuration}
                    />
                    {durationError !== null && <div className={styles.fieldError}>{durationError}</div>}
                </div>
                <Button
                    type="primary"
                    size="large"
                    className={styles.saveButton}
                    disabled={!dirty || titleError !== null || durationError !== null}
                    loading={save.isPending}
                    onClick={() => save.mutate()}
                >
                    Lưu đề
                </Button>
            </Flex>

            <div role="status" className={dirty ? styles.unsaved : styles.savedNote}>
                {dirty ? 'Có thay đổi chưa lưu.' : 'Đã lưu. Kỳ thi đã mở không bị ảnh hưởng khi sửa đề.'}
            </div>
            {save.error !== null && dirty && (
                <Alert
                    type="error"
                    showIcon
                    title={unavailable.size > 0
                        ? 'Có câu vừa bị xóa khỏi ngân hàng (đánh dấu đỏ bên dưới). Bỏ các câu đó rồi lưu lại.'
                        : save.error.message}
                />
            )}

            <div className={styles.columns}>
                <section className={styles.panel} aria-labelledby="exam-questions-title">
                    <Flex wrap justify="space-between" align="baseline" gap={8}>
                        <Typography.Title level={2} id="exam-questions-title" className={styles.panelTitle}>
                            Câu trong đề
                        </Typography.Title>
                        <Typography.Text type="secondary">{questions.length} câu, tối đa {MAX_QUESTIONS}</Typography.Text>
                    </Flex>
                    {questions.length === 0 ? (
                        <Empty description={'Đề chưa có câu nào. Thêm câu ở phần "Thêm từ ngân hàng".'} />
                    ) : (
                        <ol className={styles.rows}>
                            {questions.map((question, index) => (
                                <li key={question.id} className={styles.examRow}>
                                    <span className={styles.number}>{index + 1}</span>
                                    <div className={styles.examBody}>
                                        <div className={styles.examContent}>{question.content}</div>
                                        <QuestionOptions options={question.options} correctOption={question.correctOption} />
                                        {unavailable.has(question.id) && (
                                            <div className={styles.fieldError}>Câu này đã bị xóa khỏi ngân hàng, hãy bỏ khỏi đề.</div>
                                        )}
                                    </div>
                                    <Flex gap={6}>
                                        <Button
                                            icon={<ArrowUpOutlined />}
                                            disabled={index === 0}
                                            aria-label={`Đưa câu ${index + 1} lên`}
                                            onClick={() => setQuestions((qs) => moveQuestion(qs, index, -1))}
                                        />
                                        <Button
                                            icon={<ArrowDownOutlined />}
                                            disabled={index === questions.length - 1}
                                            aria-label={`Đưa câu ${index + 1} xuống`}
                                            onClick={() => setQuestions((qs) => moveQuestion(qs, index, 1))}
                                        />
                                        <Button
                                            type="text"
                                            danger
                                            aria-label={`Bỏ câu ${index + 1} khỏi đề`}
                                            onClick={() => setQuestions((qs) => removeQuestion(qs, question.id))}
                                        >
                                            Bỏ
                                        </Button>
                                    </Flex>
                                </li>
                            ))}
                        </ol>
                    )}
                </section>

                <QuestionBankPanel
                    addedIds={new Set(questionIds)}
                    full={full}
                    onAdd={(q) => setQuestions((qs) => addQuestion(qs, q))}
                />
            </div>
        </Flex>
    );
}
