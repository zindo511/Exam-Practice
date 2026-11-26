import { Alert, Button, Result, Skeleton } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { ApiError } from '../../shared/http';
import { StudentLayout } from '../../shared/layouts/StudentLayout';
import { getAttempt } from './api';
import { AttemptResult } from './AttemptResult';
import { remainingMs } from './clock';
import { TakeExam } from './TakeExam';

/** Tải lại định kỳ khi đang làm, để biết khi giáo viên cộng giờ hoặc khóa bài. */
const POLL_MS = 15_000;

/**
 * Hết giờ mà server chưa chốt bài (job chạy mỗi 30 giây, và tự nộp bị từ chối khi đã quá ân hạn) thì tải
 * lại dày hơn để sớm chuyển sang kết quả.
 */
const AFTER_DEADLINE_POLL_MS = 3_000;

/** Đang làm thì hiện màn làm bài (không có thanh trên của học sinh); đã kết thúc thì hiện kết quả. */
export function AttemptPage() {
    const id = Number(useParams().attemptId);
    const attempt = useQuery({
        queryKey: ['attempt', id],
        queryFn: () => getAttempt(id),
        enabled: Number.isInteger(id),
        refetchInterval: (query) => {
            const data = query.state.data;
            if (data?.status !== 'IN_PROGRESS') {
                return false;
            }
            const timeUp = remainingMs(data.deadline, data.serverTime, data.receivedAt, Date.now()) === 0;
            return timeUp ? AFTER_DEADLINE_POLL_MS : POLL_MS;
        },
    });

    const notFound = !Number.isInteger(id)
        || (attempt.error instanceof ApiError && attempt.error.code === 'ATTEMPT_NOT_FOUND');
    if (notFound) {
        return (
            <StudentLayout>
                <Result
                    status="404"
                    title="Không tìm thấy bài làm"
                    extra={<Link to="/student">Về Kỳ thi của tôi</Link>}
                />
            </StudentLayout>
        );
    }
    const data = attempt.data;
    if (data === undefined) {
        return (
            <StudentLayout>
                {attempt.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        title={attempt.error.message}
                        action={<Button onClick={() => void attempt.refetch()}>Thử lại</Button>}
                    />
                ) : <Skeleton active paragraph={{ rows: 8 }} />}
            </StudentLayout>
        );
    }
    if (data.status === 'IN_PROGRESS') {
        return <TakeExam key={data.id} attempt={data} />;
    }
    return (
        <StudentLayout>
            <AttemptResult attempt={data} />
        </StudentLayout>
    );
}
