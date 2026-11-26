import { Alert, Form, Input, InputNumber, Modal } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { createExam, type Exam, type ExamInput } from './api';

type Props = {
    onClose: () => void;
    onCreated: (exam: Exam) => void;
};

/** Tạo đề mới chỉ cần tên và thời lượng; câu hỏi thêm ở màn soạn đề. */
export function CreateExamDialog({ onClose, onCreated }: Props) {
    const [form] = Form.useForm<ExamInput>();
    const create = useMutation({
        mutationFn: (input: ExamInput) => createExam({ ...input, title: input.title.trim() }),
        onSuccess: onCreated,
    });

    return (
        <Modal
            open
            title="Tạo đề"
            okText="Tạo đề"
            cancelText="Hủy"
            confirmLoading={create.isPending}
            onOk={() => form.submit()}
            onCancel={onClose}
            mask={{ closable: false }}
        >
            <Form<ExamInput>
                form={form}
                layout="vertical"
                requiredMark={false}
                initialValues={{ durationMinutes: 45 }}
                onFinish={(input) => create.mutate(input)}
            >
                <Form.Item
                    name="title"
                    label="Tên đề"
                    rules={[
                        { required: true, whitespace: true, message: 'Nhập tên đề' },
                        { max: 200, message: 'Tối đa 200 ký tự' },
                    ]}
                >
                    <Input autoFocus onPressEnter={() => form.submit()} />
                </Form.Item>
                <Form.Item
                    name="durationMinutes"
                    label="Thời lượng (phút)"
                    rules={[{ required: true, message: 'Nhập thời lượng' }]}
                >
                    <InputNumber min={1} max={300} precision={0} onPressEnter={() => form.submit()} />
                </Form.Item>
                {create.error !== null && <Alert type="error" showIcon title={create.error.message} />}
            </Form>
        </Modal>
    );
}
