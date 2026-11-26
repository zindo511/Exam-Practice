import { Alert, App, Button, Flex, Result, Skeleton, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../../shared/http';
import { formatDateTime, formatScore } from '../sessions/format';
import { getMySession, startAttempt, type MySession } from './api';
import { ATTEMPT_LABEL } from './labels';
import styles from './MySessions.module.css';

/** Tải lại mỗi 5 giây khi đang chờ mở, để nút "Bắt đầu" hiện ngay khi giáo viên mở kỳ thi. */
const POLL_MS = 5_000;

export function WaitingRoomPage() {
    const id = Number(useParams().sessionId);
    const session = useQuery({
        queryKey: ['my-session', id],
        queryFn: () => getMySession(id),
        enabled: Number.isInteger(id),
        refetchInterval: (query) => {
            const data = query.state.data;
            return data !== undefined && data.attemptId === null && (data.status === 'SCHEDULED' || data.status === 'OPEN')
                ? POLL_MS
                : false;
        },
    });

    const notFound = !Number.isInteger(id)
        || (session.error instanceof ApiError && session.error.code === 'SESSION_NOT_FOUND');
    if (notFound) {
        return (
            <Result
                status="404"
                title="Không tìm thấy kỳ thi"
                subTitle="Bạn chưa tham gia kỳ thi này. Nhập mã giáo viên đưa để tham gia."
                extra={<Link to="/student">Về Kỳ thi của tôi</Link>}
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
        return <Skeleton active paragraph={{ rows: 6 }} />;
    }
    return <WaitingRoom session={session.data} />;
}

function WaitingRoom({ session }: { session: MySession }) {
    const { modal } = App.useApp();
    const queryClient = useQueryClient();
    const navigate = useNavigate();

    const start = useMutation({
        mutationFn: () => startAttempt(session.id),
        onSuccess: (attempt) => {
            void queryClient.invalidateQueries({ queryKey: ['my-sessions'] });
            void queryClient.invalidateQueries({ queryKey: ['my-session', session.id] });
            navigate(`/student/attempts/${attempt.id}`);
        },
        onError: (error) => {
            // Đã bắt đầu ở tab khác, hoặc kỳ thi vừa đóng: tải lại để thấy nút đúng.
            if (error instanceof ApiError
                && (error.code === 'ATTEMPT_ALREADY_STARTED' || error.code === 'SESSION_NOT_OPEN')) {
                void queryClient.invalidateQueries({ queryKey: ['my-session', session.id] });
            }
        },
    });

    function confirmStart(): void {
        modal.confirm({
            title: 'Bắt đầu làm bài?',
            content: session.durationMinutes === null
                ? 'Đồng hồ bắt đầu chạy ngay và không dừng lại.'
                : `Bạn có ${session.durationMinutes} phút. Đồng hồ bắt đầu chạy ngay và không dừng lại.`,
            okText: 'Bắt đầu',
            cancelText: 'Chưa',
            onOk: () => start.mutateAsync().catch(() => undefined),
        });
    }

    return (
        <Flex vertical gap={24}>
            <Link to="/student">Kỳ thi của tôi</Link>
            <Typography.Title level={1} className={styles.heading}>{session.title}</Typography.Title>

            <section className={styles.roomPanel} aria-live="polite">
                <RoomState session={session} />
                {session.attemptId === null && session.status === 'OPEN' && (
                    <Button type="primary" size="large" loading={start.isPending} onClick={confirmStart}>
                        Bắt đầu làm bài
                    </Button>
                )}
                {session.attemptId !== null && (
                    <Link
                        to={`/student/attempts/${session.attemptId}`}
                        className={session.attemptStatus === 'IN_PROGRESS' ? styles.primaryLink : styles.secondaryLink}
                    >
                        {session.attemptStatus === 'IN_PROGRESS' ? 'Tiếp tục làm bài' : 'Xem bài làm'}
                    </Link>
                )}
                {start.error !== null && <Alert type="error" showIcon title={start.error.message} />}
            </section>

            <div className={styles.roomColumns}>
                <section className={styles.panel} aria-labelledby="info-title">
                    <Typography.Title level={2} id="info-title" className={styles.sectionTitle}>Thông tin kỳ thi</Typography.Title>
                    <dl className={styles.facts}>
                        <dt>Giờ mở</dt>
                        <dd>{formatDateTime(session.startAt)}</dd>
                        <dt>Giờ đóng</dt>
                        <dd>{formatDateTime(session.endAt)}</dd>
                        <dt>Thời gian làm bài</dt>
                        <dd>{session.durationMinutes === null ? 'Biết khi kỳ thi mở' : `${session.durationMinutes} phút`}</dd>
                        <dt>Thang điểm</dt>
                        <dd>{session.maxScore}</dd>
                    </dl>
                </section>
                <section className={styles.panel} aria-labelledby="rules-title">
                    <Typography.Title level={2} id="rules-title" className={styles.sectionTitle}>Luật làm bài</Typography.Title>
                    <ul className={styles.rules}>
                        <li>Mỗi bạn chỉ được làm <strong>một lần</strong>.</li>
                        <li>Đồng hồ chạy từ lúc bấm "Bắt đầu" và không dừng lại, kể cả khi đóng trình duyệt.</li>
                        <li>Hạn chót không vượt quá giờ đóng kỳ thi; vào muộn thì có ít thời gian hơn.</li>
                        <li>Đáp án được <strong>tự lưu</strong> sau mỗi lần chọn. Hết giờ thì bài được tự nộp.</li>
                        <li>Rời khỏi trang làm bài hoặc thoát toàn màn hình sẽ được ghi lại cho giáo viên.</li>
                    </ul>
                </section>
            </div>
        </Flex>
    );
}

function RoomState({ session }: { session: MySession }) {
    if (session.attemptStatus !== null) {
        const finished = session.attemptStatus !== 'IN_PROGRESS';
        return (
            <div>
                <div className={styles.roomTitle}>{ATTEMPT_LABEL[session.attemptStatus]}</div>
                {finished && session.score !== null && (
                    <div>Điểm <strong>{formatScore(session.score)}</strong>/{session.maxScore}, đúng {session.correctCount} câu.</div>
                )}
                {finished && session.score === null && <div className={styles.muted}>Giáo viên chưa công bố điểm.</div>}
            </div>
        );
    }
    switch (session.status) {
        case 'SCHEDULED':
            return (
                <div>
                    <div className={styles.roomTitle}>Kỳ thi chưa mở</div>
                    <div className={styles.muted}>
                        Kỳ thi mở lúc {formatDateTime(session.startAt)}, hoặc sớm hơn nếu giáo viên mở. Cứ để trang này mở,
                        nút "Bắt đầu làm bài" sẽ tự hiện.
                    </div>
                </div>
            );
        case 'OPEN':
            return (
                <div>
                    <div className={styles.roomTitle}>Kỳ thi đã mở</div>
                    <div className={styles.muted}>Đọc luật làm bài bên dưới rồi bấm "Bắt đầu làm bài".</div>
                </div>
            );
        case 'CLOSED':
            return <div className={styles.roomTitle}>Kỳ thi đã kết thúc. Bạn không làm bài trong kỳ thi này.</div>;
        case 'CANCELLED':
            return <div className={styles.roomTitle}>Giáo viên đã hủy kỳ thi này.</div>;
    }
}
