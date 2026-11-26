import { useRef } from 'react';
import { Alert, Button, Form, Input, Modal, Radio, Typography } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { createQuestion, updateQuestion, type Question, type QuestionInput } from './api';
import styles from './QuestionFormDialog.module.css';

const MIN_OPTIONS = 2;
const MAX_OPTIONS = 6;
const LETTERS = 'ABCDEF';

type Fields = { content: string; options: string[]; correctOption?: number };

type Props = {
    /** `null` là thêm câu mới. */
    question: Question | null;
    onClose: () => void;
    onSaved: () => void;
};

/** Hộp thoại thêm và sửa câu hỏi. Mở mới mỗi lần, nên form luôn bắt đầu từ dữ liệu của câu đang chọn. */
export function QuestionFormDialog({ question, onClose, onSaved }: Props) {
    const [form] = Form.useForm<Fields>();
    const optionsRef = useRef<HTMLDivElement>(null);

    const save = useMutation({
        mutationFn: (input: QuestionInput) =>
            question === null ? createQuestion(input) : updateQuestion(question.id, input),
        onSuccess: onSaved,
    });

    function submit(fields: Fields): void {
        save.mutate({
            content: fields.content.trim(),
            options: fields.options.map((option) => option.trim()),
            correctOption: fields.correctOption ?? 0,
        });
    }

    /** Bỏ một lựa chọn thì đáp án đúng phía sau dồn lên; bỏ đúng đáp án đúng thì bắt chọn lại. */
    function onRemove(index: number, remove: (index: number) => void): void {
        const correct = form.getFieldValue('correctOption') as number | undefined;
        remove(index);
        if (correct === index) {
            form.setFieldValue('correctOption', undefined);
        } else if (correct !== undefined && correct > index) {
            form.setFieldValue('correctOption', correct - 1);
        }
    }

    /** Thêm một ô lựa chọn rồi đưa con trỏ vào ô đó. */
    function onAdd(add: (value: string) => void): void {
        add('');
        setTimeout(() => {
            const inputs = optionsRef.current?.querySelectorAll<HTMLInputElement>('input[type="text"]');
            inputs?.[inputs.length - 1]?.focus();
        });
    }

    const initialValues: Fields = question === null
        ? { content: '', options: ['', '', '', ''], correctOption: 0 }
        : { content: question.content, options: question.options, correctOption: question.correctOption };

    return (
        <Modal
            open
            title={question === null ? 'Thêm câu hỏi' : 'Sửa câu hỏi'}
            width={640}
            okText="Lưu câu hỏi"
            cancelText="Hủy"
            confirmLoading={save.isPending}
            onOk={() => form.submit()}
            onCancel={onClose}
            maskClosable={false}
        >
            <Form<Fields> form={form} layout="vertical" requiredMark={false} initialValues={initialValues} onFinish={submit}>
                <Form.Item
                    name="content"
                    label="Nội dung câu hỏi"
                    rules={[
                        { required: true, whitespace: true, message: 'Nhập nội dung câu hỏi' },
                        { max: 2000, message: 'Tối đa 2000 ký tự' },
                    ]}
                >
                    <Input.TextArea autoSize={{ minRows: 2, maxRows: 8 }} autoFocus />
                </Form.Item>

                <Form.Item
                    name="correctOption"
                    label="Lựa chọn. Chọn nút tròn ở đáp án đúng."
                    rules={[{ required: true, message: 'Chọn đáp án đúng' }]}
                >
                    <Radio.Group className={styles.options} ref={optionsRef}>
                        <Form.List name="options">
                            {(fields, { add, remove }) => (
                                <>
                                    {fields.map((field, index) => (
                                        <div key={field.key} className={styles.row}>
                                            <Radio value={index} aria-label={`Lựa chọn ${LETTERS[index]} là đáp án đúng`} />
                                            <span className={styles.letter}>{LETTERS[index]}</span>
                                            <Form.Item
                                                name={field.name}
                                                className={styles.input}
                                                rules={[
                                                    { required: true, whitespace: true, message: 'Nhập lựa chọn hoặc bỏ đi' },
                                                    { max: 500, message: 'Tối đa 500 ký tự' },
                                                ]}
                                            >
                                                <Input aria-label={`Lựa chọn ${LETTERS[index]}`} />
                                            </Form.Item>
                                            <Button
                                                type="text"
                                                danger
                                                disabled={fields.length <= MIN_OPTIONS}
                                                aria-label={`Bỏ lựa chọn ${LETTERS[index]}`}
                                                onClick={() => onRemove(field.name, remove)}
                                            >
                                                Bỏ
                                            </Button>
                                        </div>
                                    ))}
                                    {fields.length < MAX_OPTIONS && (
                                        <Button className={styles.add} onClick={() => onAdd(add)}>Thêm lựa chọn</Button>
                                    )}
                                </>
                            )}
                        </Form.List>
                    </Radio.Group>
                </Form.Item>

                {question !== null && (
                    <Typography.Paragraph type="secondary">
                        Đề đang dùng câu này sẽ thấy nội dung mới. Kỳ thi đã mở không bị ảnh hưởng.
                    </Typography.Paragraph>
                )}

                {save.error !== null && <Alert type="error" showIcon title={save.error.message} />}
            </Form>
        </Modal>
    );
}
