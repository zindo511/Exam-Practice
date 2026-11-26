import { useEffect, useState } from 'react';
import { Alert, App, Button, Empty, Flex, Input, Pagination, Skeleton, Typography } from 'antd';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../shared/http';
import { deleteQuestion, listQuestions, PAGE_SIZE, type Question } from './api';
import { QuestionFormDialog } from './QuestionFormDialog';
import styles from './QuestionListPage.module.css';
import { useDebouncedValue } from './useDebouncedValue';

const LETTERS = 'ABCDEF';

/** Chưa mở hộp thoại; `'new'` là thêm câu mới; còn lại là câu đang sửa. */
type Editing = Question | 'new' | null;

export function QuestionListPage() {
    const { message, modal } = App.useApp();
    const queryClient = useQueryClient();
    const [keyword, setKeyword] = useState('');
    const [page, setPage] = useState(0);
    const [editing, setEditing] = useState<Editing>(null);
    /** Lỗi xóa của từng câu, hiện ngay dưới câu đó. */
    const [deleteErrors, setDeleteErrors] = useState<Record<number, string>>({});
    const search = useDebouncedValue(keyword.trim(), 300);

    const questions = useQuery({
        queryKey: ['questions', page, search],
        queryFn: () => listQuestions(page, search),
        placeholderData: keepPreviousData,
    });

    // Tìm từ khóa mới thì quay về trang đầu.
    useEffect(() => setPage(0), [search]);

    // Xóa câu cuối cùng của trang cuối thì lùi về trang trước.
    const data = questions.data;
    useEffect(() => {
        if (data !== undefined && data.content.length === 0 && data.page > 0) {
            setPage(data.page - 1);
        }
    }, [data]);

    const remove = useMutation({
        mutationFn: (question: Question) => deleteQuestion(question.id),
        onSuccess: () => {
            message.success('Đã xóa câu hỏi');
            void queryClient.invalidateQueries({ queryKey: ['questions'] });
        },
        onError: (error, question) => {
            const text = error instanceof ApiError ? error.message : 'Không xóa được câu hỏi';
            setDeleteErrors((errors) => ({ ...errors, [question.id]: text }));
        },
    });

    function confirmDelete(question: Question): void {
        setDeleteErrors(({ [question.id]: _, ...rest }) => rest);
        modal.confirm({
            title: 'Xóa câu hỏi này?',
            content: question.content,
            okText: 'Xóa',
            okButtonProps: { danger: true },
            cancelText: 'Hủy',
            onOk: () => remove.mutateAsync(question).catch(() => undefined),
        });
    }

    function onSaved(): void {
        message.success('Đã lưu câu hỏi');
        setEditing(null);
        void queryClient.invalidateQueries({ queryKey: ['questions'] });
    }

    return (
        <Flex vertical gap={24}>
            <Flex wrap justify="space-between" align="center" gap={16}>
                <Typography.Title level={1} className={styles.title}>Ngân hàng câu hỏi</Typography.Title>
                <Button type="primary" size="large" onClick={() => setEditing('new')}>Thêm câu hỏi</Button>
            </Flex>

            <div className={styles.search}>
                <label htmlFor="question-search" className={styles.label}>Tìm theo nội dung</label>
                <Input
                    id="question-search"
                    size="large"
                    allowClear
                    value={keyword}
                    onChange={(e) => setKeyword(e.target.value)}
                />
            </div>

            {questions.isError && (
                <Alert
                    type="error"
                    showIcon
                    title={questions.error.message}
                    action={<Button onClick={() => void questions.refetch()}>Thử lại</Button>}
                />
            )}

            {questions.isPending && <Skeleton active paragraph={{ rows: 6 }} />}

            {data !== undefined && data.content.length === 0 && (
                <Empty
                    className={styles.empty}
                    description={search === '' ? 'Chưa có câu hỏi nào. Bấm "Thêm câu hỏi" để bắt đầu.' : 'Không tìm thấy câu hỏi nào.'}
                />
            )}

            {data !== undefined && data.content.length > 0 && (
                <>
                    <ul className={styles.list}>
                        {data.content.map((question) => (
                            <li key={question.id} className={styles.item}>
                                <div className={styles.body}>
                                    <div className={styles.content}>{question.content}</div>
                                    <div className={styles.options}>
                                        {question.options.map((option, index) => (
                                            <span
                                                key={index}
                                                className={index === question.correctOption ? styles.correct : styles.option}
                                            >
                                                {LETTERS[index]}. {option}
                                                {index === question.correctOption && <span className={styles.srOnly}> (đáp án đúng)</span>}
                                            </span>
                                        ))}
                                    </div>
                                    {deleteErrors[question.id] !== undefined && (
                                        <div role="alert" className={styles.error}>{deleteErrors[question.id]}</div>
                                    )}
                                </div>
                                <Flex gap={8}>
                                    <Button onClick={() => setEditing(question)}>Sửa</Button>
                                    <Button danger onClick={() => confirmDelete(question)}>Xóa</Button>
                                </Flex>
                            </li>
                        ))}
                    </ul>
                    <Flex wrap justify="space-between" align="center" gap={12}>
                        <Typography.Text type="secondary">{data.totalElements} câu hỏi</Typography.Text>
                        <Pagination
                            current={data.page + 1}
                            pageSize={PAGE_SIZE}
                            total={data.totalElements}
                            showSizeChanger={false}
                            hideOnSinglePage
                            onChange={(next) => setPage(next - 1)}
                        />
                    </Flex>
                </>
            )}

            {editing !== null && (
                <QuestionFormDialog
                    question={editing === 'new' ? null : editing}
                    onClose={() => setEditing(null)}
                    onSaved={onSaved}
                />
            )}
        </Flex>
    );
}
