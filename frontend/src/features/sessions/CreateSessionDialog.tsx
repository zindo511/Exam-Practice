import { useState } from 'react';
import { Alert, Form, Input, InputNumber, Modal, Radio, Select } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ApiError } from '../../shared/http';
import { useDebouncedValue } from '../../shared/useDebouncedValue';
import { listExams } from '../exams/api';
import { createSession, type ResultVisibility, type Session } from './api';
import { fromLocalInput, suggestedStart, toLocalInput, VISIBILITY_OPTIONS } from './format';
import styles from './Sessions.module.css';

type Props = {
    onClose: () => void;
    onCreated: (session: Session) => void;
};

type Values = {
    examId: number;
    startAt: string;
    endAt: string;
    maxScore: number;
    resultVisibility: ResultVisibility;
};

function initialValues(): Partial<Values> {
    const start = suggestedStart(new Date());
    return {
        startAt: toLocalInput(start),
        endAt: toLocalInput(new Date(start.getTime() + 60 * 60_000)),
        maxScore: 10,
        resultVisibility: 'SCORE',
    };
}

/** Tên kỳ thi lấy theo tên đề; mã tham gia do hệ thống sinh. */
export function CreateSessionDialog({ onClose, onCreated }: Props) {
    const [form] = Form.useForm<Values>();
    const [keyword, setKeyword] = useState('');
    const search = useDebouncedValue(keyword.trim(), 300);

    // Chỉ hiện trang đầu; giáo viên có nhiều đề thì gõ tên để tìm.
    const exams = useQuery({
        queryKey: ['exams', 0, search],
        queryFn: () => listExams(0, search),
    });

    const create = useMutation({
        mutationFn: (values: Values) => createSession({
            examId: values.examId,
            startAt: fromLocalInput(values.startAt)!,
            endAt: fromLocalInput(values.endAt)!,
            maxScore: values.maxScore,
            resultVisibility: values.resultVisibility,
        }),
        onSuccess: onCreated,
    });

    const error = create.error;
    const errorText = error instanceof ApiError && error.errors.length > 0
        ? error.errors.map((e) => e.message).join('. ')
        : error?.message;

    return (
        <Modal
            open
            title="Tạo kỳ thi"
            okText="Tạo kỳ thi"
            cancelText="Hủy"
            confirmLoading={create.isPending}
            onOk={() => form.submit()}
            onCancel={onClose}
            mask={{ closable: false }}
        >
            <Form<Values>
                form={form}
                layout="vertical"
                requiredMark={false}
                initialValues={initialValues()}
                onFinish={(values) => create.mutate(values)}
            >
                <Form.Item name="examId" label="Đề" rules={[{ required: true, message: 'Chọn đề' }]}>
                    <Select
                        showSearch={{ filterOption: false, onSearch: setKeyword }}
                        placeholder="Gõ tên đề để tìm"
                        loading={exams.isFetching}
                        notFoundContent={exams.isPending ? 'Đang tải…' : 'Không có đề nào'}
                        options={exams.data?.content.map((exam) => ({
                            value: exam.id,
                            disabled: exam.questionCount === 0,
                            label: exam.questionCount === 0
                                ? `${exam.title} (chưa có câu)`
                                : `${exam.title} · ${exam.questionCount} câu · ${exam.durationMinutes} phút`,
                        }))}
                    />
                </Form.Item>
                <div className={styles.timeFields}>
                    <Form.Item
                        name="startAt"
                        label="Giờ mở"
                        rules={[{ required: true, message: 'Chọn giờ mở' }]}
                    >
                        <Input type="datetime-local" />
                    </Form.Item>
                    <Form.Item
                        name="endAt"
                        label="Giờ đóng"
                        dependencies={['startAt']}
                        rules={[
                            { required: true, message: 'Chọn giờ đóng' },
                            ({ getFieldValue }) => ({
                                validator(_, value: string) {
                                    const end = fromLocalInput(value);
                                    const start = fromLocalInput(getFieldValue('startAt') as string);
                                    if (end === null) {
                                        return Promise.resolve();
                                    }
                                    if (new Date(end) <= new Date()) {
                                        return Promise.reject(new Error('Giờ đóng phải ở tương lai'));
                                    }
                                    if (start !== null && end <= start) {
                                        return Promise.reject(new Error('Giờ đóng phải sau giờ mở'));
                                    }
                                    return Promise.resolve();
                                },
                            }),
                        ]}
                    >
                        <Input type="datetime-local" />
                    </Form.Item>
                </div>
                <Form.Item
                    name="maxScore"
                    label="Thang điểm"
                    extra="Không đổi được sau khi tạo."
                    rules={[{ required: true, message: 'Nhập thang điểm' }]}
                >
                    <InputNumber min={1} max={100} precision={0} />
                </Form.Item>
                <Form.Item
                    name="resultVisibility"
                    label="Học sinh được xem sau khi thi"
                    extra="Đổi được bất cứ lúc nào, ví dụ công bố điểm sau khi thi."
                >
                    <Radio.Group vertical options={VISIBILITY_OPTIONS} />
                </Form.Item>
                {errorText !== undefined && <Alert type="error" showIcon title={errorText} />}
            </Form>
        </Modal>
    );
}
