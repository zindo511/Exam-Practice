import { useEffect, useState } from 'react';
import { Alert, App, Button, Empty, Flex, Input, Skeleton, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { useDebouncedValue } from '../../shared/useDebouncedValue';
import { deleteExam, listExams, PAGE_SIZE, type Exam } from './api';
import { CreateExamDialog } from './CreateExamDialog';
import styles from './ExamListPage.module.css';

const DATE = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });

export function ExamListPage() {
    const { message, modal } = App.useApp();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const [keyword, setKeyword] = useState('');
    const [page, setPage] = useState(0);
    const [creating, setCreating] = useState(false);
    const search = useDebouncedValue(keyword.trim(), 300);

    const exams = useQuery({
        queryKey: ['exams', page, search],
        queryFn: () => listExams(page, search),
        placeholderData: keepPreviousData,
    });

    useEffect(() => setPage(0), [search]);

    // Xóa đề cuối cùng của trang cuối thì lùi về trang trước.
    const data = exams.data;
    useEffect(() => {
        if (data !== undefined && data.content.length === 0 && data.page > 0) {
            setPage(data.page - 1);
        }
    }, [data]);

    const remove = useMutation({
        mutationFn: (exam: Exam) => deleteExam(exam.id),
        onSuccess: () => {
            message.success('Đã xóa đề');
            void queryClient.invalidateQueries({ queryKey: ['exams'] });
        },
        onError: (error) => message.error(error.message),
    });

    function confirmDelete(exam: Exam): void {
        modal.confirm({
            title: `Xóa đề "${exam.title}"?`,
            content: 'Kỳ thi đã lên lịch từ đề này sẽ không mở được. Kỳ thi đã mở hoặc đã kết thúc không bị ảnh hưởng.',
            okText: 'Xóa',
            okButtonProps: { danger: true },
            cancelText: 'Hủy',
            onOk: () => remove.mutateAsync(exam).catch(() => undefined),
        });
    }

    const columns: ColumnsType<Exam> = [
        {
            title: 'Tên đề',
            key: 'title',
            render: (_, exam) => (
                <>
                    <Link to={`/teacher/exams/${exam.id}`} className={styles.title}>{exam.title}</Link>
                    {exam.questionCount === 0 && (
                        <div className={styles.warning}>Chưa có câu nào, chưa dùng được cho kỳ thi.</div>
                    )}
                </>
            ),
        },
        { title: 'Số câu', dataIndex: 'questionCount', width: 100 },
        { title: 'Thời lượng', key: 'duration', width: 130, render: (_, exam) => `${exam.durationMinutes} phút` },
        { title: 'Sửa lần cuối', key: 'updatedAt', width: 150, render: (_, exam) => DATE.format(new Date(exam.updatedAt)) },
        {
            key: 'actions',
            width: 100,
            align: 'right',
            render: (_, exam) => (
                <Button danger aria-label={`Xóa đề ${exam.title}`} onClick={() => confirmDelete(exam)}>Xóa</Button>
            ),
        },
    ];

    return (
        <Flex vertical gap={24}>
            <Flex wrap justify="space-between" align="center" gap={16}>
                <Typography.Title level={1} className={styles.heading}>Đề thi</Typography.Title>
                <Button type="primary" size="large" onClick={() => setCreating(true)}>Tạo đề</Button>
            </Flex>

            <div className={styles.search}>
                <label htmlFor="exam-search" className={styles.label}>Tìm theo tên đề</label>
                <Input id="exam-search" size="large" allowClear value={keyword} onChange={(e) => setKeyword(e.target.value)} />
            </div>

            {exams.isError && (
                <Alert
                    type="error"
                    showIcon
                    title={exams.error.message}
                    action={<Button onClick={() => void exams.refetch()}>Thử lại</Button>}
                />
            )}

            {exams.isPending && <Skeleton active paragraph={{ rows: 6 }} />}

            {data !== undefined && (
                <Table<Exam>
                    className={styles.table}
                    rowKey="id"
                    columns={columns}
                    dataSource={data.content}
                    loading={exams.isFetching && exams.isPlaceholderData}
                    scroll={{ x: 720 }}
                    locale={{
                        emptyText: (
                            <Empty description={search === '' ? 'Chưa có đề nào. Bấm "Tạo đề" để bắt đầu.' : 'Không tìm thấy đề nào.'} />
                        ),
                    }}
                    pagination={{
                        current: data.page + 1,
                        pageSize: PAGE_SIZE,
                        total: data.totalElements,
                        showSizeChanger: false,
                        hideOnSinglePage: true,
                        showTotal: (total) => `${total} đề`,
                        onChange: (next) => setPage(next - 1),
                    }}
                />
            )}

            {creating && (
                <CreateExamDialog
                    onClose={() => setCreating(false)}
                    onCreated={(exam) => {
                        void queryClient.invalidateQueries({ queryKey: ['exams'] });
                        navigate(`/teacher/exams/${exam.id}`);
                    }}
                />
            )}
        </Flex>
    );
}
