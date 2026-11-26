import { Alert, App, Button, Flex, Result, Select, Skeleton, Typography } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { ApiError } from '../../shared/http';
import {
    cancelSession,
    changeResultVisibility,
    closeSession,
    getSession,
    openSession,
    setJoinOpen,
    type ResultVisibility,
    type Session,
} from './api';
import { formatDateTime, VISIBILITY_OPTIONS } from './format';
import { SessionResults } from './SessionResults';
import styles from './Sessions.module.css';
import { SessionStatusTag } from './SessionStatusTag';

export function SessionDetailPage() {
    const id = Number(useParams().sessionId);
    const session = useQuery({
        queryKey: ['session', id],
        queryFn: () => getSession(id),
        enabled: Number.isInteger(id),
        // Kỳ thi tự mở, tự đóng theo giờ; tải lại để nút và trạng thái đúng với server.
        refetchInterval: (query) => {
            const status = query.state.data?.status;
            return status === 'SCHEDULED' || status === 'OPEN' ? 10_000 : false;
        },
    });

    const notFound = !Number.isInteger(id)
        || (session.error instanceof ApiError && session.error.code === 'SESSION_NOT_FOUND');
    if (notFound) {
        return (
            <Result
                status="404"
                title="Không tìm thấy kỳ thi"
                subTitle="Kỳ thi không tồn tại hoặc thuộc giáo viên khác."
                extra={<Link to="/teacher/sessions">Về danh sách kỳ thi</Link>}
            />
        );
    }
    if (session.data === undefined) {
        if (session.isError) {
            return (
                <Alert
                    type="error"
                    showIcon
                    title={session.error.message}
                    action={<Button onClick={() => void session.refetch()}>Thử lại</Button>}
                />
            );
        }
        return <Skeleton active paragraph={{ rows: 8 }} />;
    }
    return <SessionDetail session={session.data} />;
}

function SessionDetail({ session }: { session: Session }) {
    const { message, modal } = App.useApp();
    const queryClient = useQueryClient();

    /** Mọi thao tác trả về kỳ thi mới; sai trạng thái (ví dụ job vừa mở kỳ thi) thì tải lại để thấy trạng thái thật. */
    const action = useMutation({
        mutationFn: (run: () => Promise<Session>) => run(),
        onSuccess: (updated) => {
            queryClient.setQueryData(['session', session.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['sessions'] });
            void queryClient.invalidateQueries({ queryKey: ['session-results', session.id] });
        },
        onError: (error) => {
            message.error(error.message);
            if (error instanceof ApiError && error.code === 'INVALID_SESSION_STATE') {
                void queryClient.invalidateQueries({ queryKey: ['session', session.id] });
            }
        },
    });

    function confirm(title: string, content: string, okText: string, run: () => Promise<Session>, danger = false): void {
        modal.confirm({
            title,
            content,
            okText,
            okButtonProps: { danger },
            cancelText: 'Không',
            onOk: () => action.mutateAsync(run).catch(() => undefined),
        });
    }

    async function copyCode(): Promise<void> {
        try {
            await navigator.clipboard.writeText(session.accessCode);
            message.success('Đã sao chép mã');
        } catch {
            message.error('Không sao chép được, hãy chép tay mã trên màn hình.');
        }
    }

    const active = session.status === 'SCHEDULED' || session.status === 'OPEN';

    return (
        <Flex vertical gap={20}>
            <Link to="/teacher/sessions">Tất cả kỳ thi</Link>

            <Flex wrap justify="space-between" align="center" gap={16}>
                <Flex wrap align="center" gap={12}>
                    <Typography.Title level={1} className={styles.heading}>{session.title}</Typography.Title>
                    <SessionStatusTag status={session.status} />
                </Flex>
                <Flex wrap gap={8}>
                    {session.status === 'SCHEDULED' && (
                        <>
                            <Button
                                danger
                                onClick={() => confirm(
                                    'Hủy kỳ thi?',
                                    'Mã tham gia hết hiệu lực. Không hoàn tác được.',
                                    'Hủy kỳ thi',
                                    () => cancelSession(session.id),
                                    true,
                                )}
                            >
                                Hủy kỳ thi
                            </Button>
                            <Button
                                type="primary"
                                onClick={() => confirm(
                                    'Mở kỳ thi ngay?',
                                    'Học sinh đã vào phòng chờ có thể bắt đầu làm bài. Đề được chụp lúc này, sửa đề sau đó không ảnh hưởng kỳ thi.',
                                    'Mở ngay',
                                    () => openSession(session.id),
                                )}
                            >
                                Mở sớm
                            </Button>
                        </>
                    )}
                    {session.status === 'OPEN' && (
                        <Button
                            danger
                            onClick={() => confirm(
                                'Kết thúc kỳ thi ngay?',
                                'Bài đang làm được chốt và chấm từ đáp án đã lưu. Không hoàn tác được.',
                                'Kết thúc',
                                () => closeSession(session.id),
                                true,
                            )}
                        >
                            Kết thúc sớm
                        </Button>
                    )}
                </Flex>
            </Flex>

            {session.examUsable === false && (
                <Alert
                    type="warning"
                    showIcon
                    title="Đề của kỳ thi không còn dùng được (đã xóa hoặc không còn câu nào)."
                    description={<>Tới giờ mở, kỳ thi sẽ tự hủy. <Link to={`/teacher/exams/${session.examId}`}>Sửa đề</Link> trước giờ mở, hoặc hủy kỳ thi này và tạo kỳ thi khác.</>}
                />
            )}

            <div className={styles.overview}>
                <section className={styles.codePanel} aria-labelledby="access-code-label">
                    <div id="access-code-label" className={styles.label}>Mã tham gia</div>
                    <Flex align="center" gap={12}>
                        <span className={styles.bigCode}>{session.accessCode}</span>
                        <Button icon={<CopyOutlined />} aria-label="Sao chép mã tham gia" onClick={() => void copyCode()} />
                    </Flex>
                    {active ? (
                        <Flex wrap align="center" gap={12}>
                            <span className={session.joinOpen ? styles.muted : styles.warning}>
                                {session.joinOpen ? 'Học sinh đang nhập mã được.' : 'Đã khóa: học sinh mới không vào được.'}
                            </span>
                            <Button
                                disabled={action.isPending}
                                onClick={() => action.mutate(() => setJoinOpen(session.id, !session.joinOpen))}
                            >
                                {session.joinOpen ? 'Khóa tham gia' : 'Mở lại tham gia'}
                            </Button>
                        </Flex>
                    ) : (
                        <span className={styles.muted}>Mã không còn dùng được.</span>
                    )}
                </section>

                <section className={styles.panel} aria-label="Thông tin kỳ thi">
                    <dl className={styles.facts}>
                        <dt>Đề</dt>
                        <dd><Link to={`/teacher/exams/${session.examId}`}>Xem đề gốc</Link></dd>
                        <dt>Giờ mở</dt>
                        <dd>{formatDateTime(session.startAt)}</dd>
                        <dt>Giờ đóng</dt>
                        <dd>{formatDateTime(session.endAt)}</dd>
                        <dt>Thời lượng</dt>
                        <dd>{session.durationMinutes === null ? 'Lấy từ đề khi mở kỳ thi' : `${session.durationMinutes} phút`}</dd>
                        <dt>Thang điểm</dt>
                        <dd>{session.maxScore}</dd>
                        {session.openedAt !== null && (
                            <>
                                <dt>Đã mở lúc</dt>
                                <dd>{formatDateTime(session.openedAt)}</dd>
                            </>
                        )}
                        {session.closedAt !== null && (
                            <>
                                <dt>Đã đóng lúc</dt>
                                <dd>{formatDateTime(session.closedAt)}</dd>
                            </>
                        )}
                    </dl>
                    <div className={styles.field}>
                        <label htmlFor="result-visibility" className={styles.label}>Học sinh được xem sau khi thi</label>
                        <Select<ResultVisibility>
                            id="result-visibility"
                            className={styles.visibility}
                            value={session.resultVisibility}
                            options={VISIBILITY_OPTIONS}
                            popupMatchSelectWidth={false}
                            disabled={action.isPending}
                            onChange={(value) => action.mutate(() => changeResultVisibility(session.id, value))}
                        />
                    </div>
                </section>
            </div>

            {session.status !== 'CANCELLED' && <SessionResults session={session} />}
        </Flex>
    );
}
