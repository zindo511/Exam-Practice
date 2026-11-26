import { useEffect, useState } from 'react';
import { Alert, Button, Empty, Flex, Input, Pagination, Skeleton, Typography } from 'antd';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useDebouncedValue } from '../../shared/useDebouncedValue';
import { listQuestions, PAGE_SIZE, type Question } from '../questions/api';
import styles from './ExamEditorPage.module.css';

type Props = {
    /** ID các câu đang có trong đề, để hiện "Đã thêm". */
    addedIds: Set<number>;
    /** Đề đã đủ số câu tối đa. */
    full: boolean;
    onAdd: (question: Question) => void;
};

/** Cột bên phải màn soạn đề: tìm câu trong ngân hàng để thêm vào đề. */
export function QuestionBankPanel({ addedIds, full, onAdd }: Props) {
    const [keyword, setKeyword] = useState('');
    const [page, setPage] = useState(0);
    const search = useDebouncedValue(keyword.trim(), 300);

    const questions = useQuery({
        queryKey: ['questions', page, search],
        queryFn: () => listQuestions(page, search),
        placeholderData: keepPreviousData,
    });

    useEffect(() => setPage(0), [search]);

    const data = questions.data;

    return (
        <section className={styles.panel} aria-labelledby="bank-title">
            <Typography.Title level={2} id="bank-title" className={styles.panelTitle}>Thêm từ ngân hàng</Typography.Title>
            <div className={styles.field}>
                <label htmlFor="bank-search" className={styles.label}>Tìm câu hỏi</label>
                <Input id="bank-search" allowClear value={keyword} onChange={(e) => setKeyword(e.target.value)} />
            </div>

            {full && <Alert type="warning" showIcon title="Đề đã đủ 100 câu, không thêm được nữa." />}
            {questions.isError && (
                <Alert
                    type="error"
                    showIcon
                    title={questions.error.message}
                    action={<Button onClick={() => void questions.refetch()}>Thử lại</Button>}
                />
            )}
            {questions.isPending && <Skeleton active />}

            {data !== undefined && data.content.length === 0 && (
                <Empty
                    description={
                        search === ''
                            ? <>Ngân hàng chưa có câu nào. <Link to="/teacher/questions">Thêm câu hỏi</Link></>
                            : 'Không tìm thấy câu hỏi nào.'
                    }
                />
            )}

            {data !== undefined && data.content.length > 0 && (
                <>
                    <ul className={styles.rows}>
                        {data.content.map((question) => {
                            const added = addedIds.has(question.id);
                            return (
                                <li key={question.id} className={styles.bankRow}>
                                    <span className={styles.bankContent}>{question.content}</span>
                                    <Button
                                        disabled={added || full}
                                        aria-label={added ? `Đã thêm: ${question.content}` : `Thêm câu: ${question.content}`}
                                        onClick={() => onAdd(question)}
                                    >
                                        {added ? 'Đã thêm' : 'Thêm'}
                                    </Button>
                                </li>
                            );
                        })}
                    </ul>
                    <Flex justify="flex-end">
                        <Pagination
                            size="small"
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
        </section>
    );
}
