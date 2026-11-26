import { useEffect, useState } from 'react';
import { Alert, Button, Empty, Flex, Segmented, Skeleton, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getResults, PAGE_SIZE, type ResultFilter, type Session, type StudentResultRow } from './api';
import { AttemptDialog } from './AttemptDialog';
import { ATTEMPT_STATUS_LABEL, formatDateTime, formatScore } from './format';
import styles from './Sessions.module.css';

type Filter = ResultFilter | 'ALL';

const FILTERS: { value: Filter; label: string }[] = [
    { value: 'ALL', label: 'Tất cả' },
    { value: 'NOT_STARTED', label: 'Chưa làm' },
    ...(['IN_PROGRESS', 'SUBMITTED', 'EXPIRED', 'LOCKED'] as const).map((s) => ({ value: s, label: ATTEMPT_STATUS_LABEL[s] })),
];

/** Học sinh đã nhập mã và điểm của từng người; giáo viên luôn thấy điểm, không phụ thuộc cài đặt của học sinh. */
export function SessionResults({ session }: { session: Session }) {
    const [filter, setFilter] = useState<Filter>('ALL');
    const [page, setPage] = useState(0);
    const [viewing, setViewing] = useState<number | null>(null);

    const results = useQuery({
        queryKey: ['session-results', session.id, page, filter],
        queryFn: () => getResults(session.id, page, filter === 'ALL' ? null : filter),
        placeholderData: keepPreviousData,
        // Lúc chờ và lúc thi học sinh vào, nộp liên tục; xong rồi thì không đổi nữa.
        refetchInterval: session.status === 'CLOSED' ? false : 10_000,
    });

    useEffect(() => setPage(0), [filter]);

    const data = results.data;
    const columns: ColumnsType<StudentResultRow> = [
        {
            title: 'Học sinh',
            key: 'student',
            width: 220,
            render: (_, row) => (
                <>
                    <div className={styles.studentName}>{row.fullName ?? 'Không rõ tên'}</div>
                    {row.username !== null && <div className={styles.muted}>{row.username}</div>}
                </>
            ),
        },
        {
            title: 'Trạng thái',
            key: 'status',
            width: 130,
            render: (_, row) => (row.attemptStatus === null
                ? <span className={styles.muted}>Chưa làm</span>
                : <span className={row.attemptStatus === 'LOCKED' ? styles.warning : undefined}>{ATTEMPT_STATUS_LABEL[row.attemptStatus]}</span>),
        },
        {
            title: 'Số câu đúng',
            key: 'correct',
            width: 120,
            render: (_, row) => (row.correctCount === null ? '—' : `${row.correctCount}/${data?.totalQuestions ?? 0}`),
        },
        {
            title: 'Điểm',
            key: 'score',
            width: 100,
            render: (_, row) => (row.score === null ? '—' : <strong>{formatScore(row.score)}</strong>),
        },
        {
            title: 'Kết thúc lúc',
            key: 'submittedAt',
            width: 170,
            render: (_, row) => (row.submittedAt === null ? '—' : formatDateTime(row.submittedAt)),
        },
        {
            key: 'actions',
            width: 110,
            align: 'right',
            render: (_, row) => row.attemptId !== null && (
                <Button
                    aria-label={`Xem bài của ${row.fullName ?? row.username ?? 'học sinh'}`}
                    onClick={() => setViewing(row.attemptId)}
                >
                    Xem bài
                </Button>
            ),
        },
    ];

    const summary = data?.summary;

    return (
        <section className={styles.panel} aria-labelledby="results-title">
            <Flex wrap justify="space-between" align="baseline" gap={8}>
                <Typography.Title level={2} id="results-title" className={styles.panelTitle}>Học sinh và điểm</Typography.Title>
                {data !== undefined && (
                    <Typography.Text type="secondary">
                        Thang điểm {data.maxScore}{data.totalQuestions > 0 && `, ${data.totalQuestions} câu`}
                    </Typography.Text>
                )}
            </Flex>

            {summary !== undefined && (
                <dl className={styles.stats}>
                    <div><dt>Đã vào</dt><dd>{summary.participantCount}</dd></div>
                    <div><dt>Chưa làm</dt><dd>{summary.notStartedCount}</dd></div>
                    <div><dt>Đang làm</dt><dd>{summary.inProgressCount}</dd></div>
                    <div><dt>Đã xong</dt><dd>{summary.finishedCount}</dd></div>
                    <div><dt>Trung bình</dt><dd>{summary.averageScore === null ? '—' : formatScore(summary.averageScore)}</dd></div>
                    <div><dt>Cao nhất</dt><dd>{summary.highestScore === null ? '—' : formatScore(summary.highestScore)}</dd></div>
                    <div><dt>Thấp nhất</dt><dd>{summary.lowestScore === null ? '—' : formatScore(summary.lowestScore)}</dd></div>
                </dl>
            )}

            <Segmented<Filter>
                className={styles.filter}
                aria-label="Lọc học sinh theo trạng thái bài làm"
                options={FILTERS}
                value={filter}
                onChange={setFilter}
            />

            {results.isError && (
                <Alert
                    type="error"
                    showIcon
                    title={results.error.message}
                    action={<Button onClick={() => void results.refetch()}>Thử lại</Button>}
                />
            )}

            {results.isPending && <Skeleton active paragraph={{ rows: 4 }} />}

            {data !== undefined && (
                <Table<StudentResultRow>
                    rowKey="studentId"
                    columns={columns}
                    dataSource={data.students.content}
                    loading={results.isFetching && results.isPlaceholderData}
                    scroll={{ x: 850 }}
                    locale={{
                        emptyText: (
                            <Empty description={filter === 'ALL'
                                ? 'Chưa có học sinh nào nhập mã.'
                                : 'Không có học sinh nào ở trạng thái này.'}
                            />
                        ),
                    }}
                    pagination={{
                        current: data.students.page + 1,
                        pageSize: PAGE_SIZE,
                        total: data.students.totalElements,
                        showSizeChanger: false,
                        hideOnSinglePage: true,
                        showTotal: (total) => `${total} học sinh`,
                        onChange: (next) => setPage(next - 1),
                    }}
                />
            )}

            {viewing !== null && (
                <AttemptDialog sessionId={session.id} attemptId={viewing} onClose={() => setViewing(null)} />
            )}
        </section>
    );
}
