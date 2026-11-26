import { useEffect, useState } from 'react';
import { Alert, Button, Empty, Flex, Input, Pagination, Skeleton, Typography } from 'antd';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '../../shared/http';
import { formatDateTime, formatScore, SESSION_STATUS_LABEL } from '../sessions/format';
import { joinSession, listMySessions, PAGE_SIZE, type MySession } from './api';
import { ATTEMPT_LABEL } from './labels';
import styles from './MySessions.module.css';
import { nextStep } from './nextStep';

export function MySessionsPage() {
    const [page, setPage] = useState(0);
    const sessions = useQuery({
        queryKey: ['my-sessions', page],
        queryFn: () => listMySessions(page),
        placeholderData: keepPreviousData,
        // Kỳ thi tự mở, tự đóng theo giờ; tải lại để nút đúng với trạng thái mới.
        refetchInterval: 30_000,
    });
    const data = sessions.data;

    return (
        <Flex vertical gap={28}>
            <Typography.Title level={1} className={styles.heading}>Kỳ thi của tôi</Typography.Title>

            <JoinForm />

            <section aria-labelledby="joined-title">
                <Typography.Title level={2} id="joined-title" className={styles.sectionTitle}>
                    Kỳ thi đã tham gia
                </Typography.Title>
                {sessions.isError && (
                    <Alert
                        type="error"
                        showIcon
                        title={sessions.error.message}
                        action={<Button onClick={() => void sessions.refetch()}>Thử lại</Button>}
                    />
                )}
                {sessions.isPending && <Skeleton active paragraph={{ rows: 4 }} />}
                {data !== undefined && data.content.length === 0 && (
                    <Empty description="Chưa tham gia kỳ thi nào. Nhập mã giáo viên đưa để bắt đầu." />
                )}
                {data !== undefined && data.content.length > 0 && (
                    <>
                        <ul className={styles.list}>
                            {data.content.map((session) => <SessionCard key={session.id} session={session} />)}
                        </ul>
                        <Pagination
                            className={styles.pagination}
                            current={data.page + 1}
                            pageSize={PAGE_SIZE}
                            total={data.totalElements}
                            hideOnSinglePage
                            showSizeChanger={false}
                            onChange={(next) => setPage(next - 1)}
                        />
                    </>
                )}
            </section>
        </Flex>
    );
}

/** Nhập mã giáo viên đưa; đúng thì vào thẳng phòng chờ. */
function JoinForm() {
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const [code, setCode] = useState('');
    const [touched, setTouched] = useState(false);

    const join = useMutation({
        mutationFn: joinSession,
        onSuccess: (session) => {
            queryClient.setQueryData(['my-session', session.id], session);
            void queryClient.invalidateQueries({ queryKey: ['my-sessions'] });
            navigate(`/student/sessions/${session.id}`);
        },
    });

    // Sửa mã thì ẩn lỗi cũ, để học sinh không tưởng mã mới cũng sai.
    const { reset } = join;
    useEffect(() => reset(), [code, reset]);

    const trimmed = code.trim();
    const emptyError = touched && trimmed === '' ? 'Nhập mã tham gia' : null;
    const error = join.error;
    const errorText = emptyError ?? (error === null ? null : joinErrorText(error));

    function submit(): void {
        setTouched(true);
        if (trimmed !== '') {
            join.mutate(trimmed);
        }
    }

    return (
        <section className={styles.joinPanel} aria-labelledby="join-title">
            <Typography.Title level={2} id="join-title" className={styles.sectionTitle}>Tham gia kỳ thi</Typography.Title>
            <form
                className={styles.joinForm}
                noValidate
                onSubmit={(e) => {
                    e.preventDefault();
                    submit();
                }}
            >
                <div className={styles.joinField}>
                    <label htmlFor="access-code" className={styles.label}>Mã tham gia</label>
                    <Input
                        id="access-code"
                        size="large"
                        className={styles.codeInput}
                        maxLength={20}
                        autoComplete="off"
                        spellCheck={false}
                        placeholder="VD: K7Q2MX"
                        value={code}
                        status={errorText === null ? undefined : 'error'}
                        aria-describedby={errorText === null ? 'access-code-hint' : 'access-code-error'}
                        onChange={(e) => setCode(e.target.value.toUpperCase())}
                    />
                    {errorText === null
                        ? <div id="access-code-hint" className={styles.muted}>Mã gồm 6 ký tự, giáo viên đưa trước giờ thi.</div>
                        : <div id="access-code-error" role="alert" className={styles.fieldError}>{errorText}</div>}
                </div>
                <Button type="primary" size="large" htmlType="submit" loading={join.isPending} className={styles.joinButton}>
                    Tham gia
                </Button>
            </form>
        </section>
    );
}

function joinErrorText(error: Error): string {
    if (error instanceof ApiError && error.code === 'INVALID_ACCESS_CODE') {
        return 'Mã không đúng, hoặc kỳ thi đã khóa tham gia hay đã kết thúc. Kiểm tra lại mã với giáo viên.';
    }
    // Thông báo chặn của backend có kèm thời gian chờ, nên dùng nguyên văn.
    return error.message;
}

function SessionCard({ session }: { session: MySession }) {
    const step = nextStep(session);
    const finished = session.attemptStatus !== null && session.attemptStatus !== 'IN_PROGRESS';

    return (
        <li className={styles.card}>
            <div className={styles.cardBody}>
                <div className={styles.cardTitle}>{session.title}</div>
                <div className={styles.muted}>
                    {formatDateTime(session.startAt)} – {formatDateTime(session.endAt)}
                </div>
                <div className={styles.cardStatus}>
                    {session.attemptStatus === null
                        ? SESSION_STATUS_LABEL[session.status]
                        : ATTEMPT_LABEL[session.attemptStatus]}
                    {finished && session.score !== null && (
                        <> · Điểm <strong>{formatScore(session.score)}</strong>/{session.maxScore}</>
                    )}
                    {finished && session.score === null && ' · Giáo viên chưa công bố điểm'}
                </div>
                {step.note !== null && <div className={styles.muted}>{step.note}</div>}
            </div>
            {step.to !== null && (
                <Link to={step.to} className={step.primary ? styles.primaryLink : styles.secondaryLink}>
                    {step.label}
                </Link>
            )}
        </li>
    );
}
