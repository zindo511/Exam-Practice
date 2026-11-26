import { useEffect, useState } from 'react';
import { Alert, Button, Empty, Flex, Segmented, Skeleton, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { listSessions, PAGE_SIZE, type Session, type SessionStatus } from './api';
import { CreateSessionDialog } from './CreateSessionDialog';
import { formatDateTime, SESSION_STATUS_LABEL } from './format';
import styles from './Sessions.module.css';
import { SessionStatusTag } from './SessionStatusTag';

type Filter = SessionStatus | 'ALL';

const FILTERS: { value: Filter; label: string }[] = [
    { value: 'ALL', label: 'Tất cả' },
    ...(['SCHEDULED', 'OPEN', 'CLOSED', 'CANCELLED'] as const).map((s) => ({ value: s, label: SESSION_STATUS_LABEL[s] })),
];

export function SessionListPage() {
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const [filter, setFilter] = useState<Filter>('ALL');
    const [page, setPage] = useState(0);
    const [creating, setCreating] = useState(false);

    const sessions = useQuery({
        queryKey: ['sessions', page, filter],
        queryFn: () => listSessions(page, filter === 'ALL' ? null : filter),
        placeholderData: keepPreviousData,
        // Kỳ thi tự mở, tự đóng theo giờ, nên tải lại định kỳ để trạng thái không cũ.
        refetchInterval: 30_000,
    });

    useEffect(() => setPage(0), [filter]);

    const data = sessions.data;
    const columns: ColumnsType<Session> = [
        {
            title: 'Kỳ thi',
            key: 'title',
            render: (_, session) => (
                <>
                    <Link to={`/teacher/sessions/${session.id}`} className={styles.title}>{session.title}</Link>
                    {session.examUsable === false && (
                        <div className={styles.warning}>Đề không còn dùng được: tới giờ kỳ thi sẽ bị hủy.</div>
                    )}
                </>
            ),
        },
        {
            title: 'Mã tham gia',
            key: 'accessCode',
            width: 140,
            render: (_, session) => (
                <>
                    <span className={styles.code}>{session.accessCode}</span>
                    {!session.joinOpen && (session.status === 'SCHEDULED' || session.status === 'OPEN') && (
                        <div className={styles.muted}>Đã khóa tham gia</div>
                    )}
                </>
            ),
        },
        {
            title: 'Thời gian',
            key: 'time',
            width: 210,
            render: (_, session) => (
                <>
                    <div>Mở {formatDateTime(session.startAt)}</div>
                    <div className={styles.muted}>Đóng {formatDateTime(session.endAt)}</div>
                </>
            ),
        },
        {
            title: 'Trạng thái',
            key: 'status',
            width: 140,
            render: (_, session) => <SessionStatusTag status={session.status} />,
        },
    ];

    return (
        <Flex vertical gap={24}>
            <Flex wrap justify="space-between" align="center" gap={16}>
                <Typography.Title level={1} className={styles.heading}>Kỳ thi</Typography.Title>
                <Button type="primary" size="large" onClick={() => setCreating(true)}>Tạo kỳ thi</Button>
            </Flex>

            <Segmented<Filter>
                className={styles.filter}
                aria-label="Lọc theo trạng thái"
                options={FILTERS}
                value={filter}
                onChange={setFilter}
            />

            {sessions.isError && (
                <Alert
                    type="error"
                    showIcon
                    title={sessions.error.message}
                    action={<Button onClick={() => void sessions.refetch()}>Thử lại</Button>}
                />
            )}

            {sessions.isPending && <Skeleton active paragraph={{ rows: 6 }} />}

            {data !== undefined && (
                <Table<Session>
                    className={styles.table}
                    rowKey="id"
                    columns={columns}
                    dataSource={data.content}
                    loading={sessions.isFetching && sessions.isPlaceholderData}
                    scroll={{ x: 720 }}
                    locale={{
                        emptyText: (
                            <Empty description={filter === 'ALL'
                                ? 'Chưa có kỳ thi nào. Bấm "Tạo kỳ thi" để bắt đầu.'
                                : 'Không có kỳ thi nào ở trạng thái này.'}
                            />
                        ),
                    }}
                    pagination={{
                        current: data.page + 1,
                        pageSize: PAGE_SIZE,
                        total: data.totalElements,
                        showSizeChanger: false,
                        hideOnSinglePage: true,
                        showTotal: (total) => `${total} kỳ thi`,
                        onChange: (next) => setPage(next - 1),
                    }}
                />
            )}

            {creating && (
                <CreateSessionDialog
                    onClose={() => setCreating(false)}
                    onCreated={(session) => {
                        void queryClient.invalidateQueries({ queryKey: ['sessions'] });
                        queryClient.setQueryData(['session', session.id], session);
                        navigate(`/teacher/sessions/${session.id}`);
                    }}
                />
            )}
        </Flex>
    );
}
