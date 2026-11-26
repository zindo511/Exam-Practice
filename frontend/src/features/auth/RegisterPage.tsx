import { Alert, Button, Form, Input, Radio, Typography } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { Link, Navigate } from 'react-router';
import { ApiError } from '../../shared/http';
import { register, type RegisterForm } from './api';
import { useAuth } from './AuthContext';
import styles from './AuthPage.module.css';
import { HOME } from './RequireRole';

type Fields = RegisterForm & { confirmPassword: string };

const FIELDS: (keyof RegisterForm)[] = ['role', 'fullName', 'username', 'password'];

export function RegisterPage() {
    const { user, hasToken, login } = useAuth();
    const [form] = Form.useForm<Fields>();

    /** Tạo tài khoản rồi đăng nhập luôn, để người dùng không phải gõ lại. */
    const submit = useMutation({
        mutationFn: async ({ role, fullName, username, password }: Fields) => {
            await register({ role, fullName, username, password });
            try {
                await login(username, password);
            } catch {
                throw new Error('Đã tạo tài khoản nhưng chưa đăng nhập được. Mở màn đăng nhập để vào.');
            }
        },
        onError: (error) => showOnFields(error),
    });

    /** Lỗi của từng ô thì hiện dưới ô đó; lỗi khác hiện ở khung chung. */
    function showOnFields(error: Error): void {
        if (!(error instanceof ApiError)) {
            return;
        }
        if (error.code === 'USERNAME_TAKEN') {
            form.setFields([{ name: 'username', errors: [error.message] }]);
        }
        for (const { field, message } of error.errors) {
            if (FIELDS.includes(field as keyof RegisterForm)) {
                form.setFields([{ name: field as keyof RegisterForm, errors: [message] }]);
            }
        }
    }

    const generalError = submit.error !== null
        && !(submit.error instanceof ApiError
            && (submit.error.code === 'USERNAME_TAKEN' || submit.error.code === 'VALIDATION_FAILED'));

    if (user !== null) {
        return <Navigate to={HOME[user.role]} replace />;
    }
    if (hasToken) {
        return <Navigate to={HOME.STUDENT} replace />;
    }

    return (
        <div className={styles.page}>
            <div className={styles.brand}>Exam Practice</div>
            <main className={styles.card}>
                <Typography.Title level={1} className={styles.title}>Đăng ký</Typography.Title>
                <Form<Fields>
                    form={form}
                    layout="vertical"
                    size="large"
                    requiredMark={false}
                    initialValues={{ role: 'STUDENT' }}
                    onFinish={(fields) => submit.mutate(fields)}
                >
                    <Form.Item name="role" label="Bạn là">
                        <Radio.Group
                            block
                            optionType="button"
                            buttonStyle="solid"
                            options={[
                                { value: 'STUDENT', label: 'Học sinh' },
                                { value: 'TEACHER', label: 'Giáo viên' },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item
                        name="fullName"
                        label="Họ và tên"
                        rules={[
                            { required: true, whitespace: true, message: 'Nhập họ và tên' },
                            { max: 100, message: 'Tối đa 100 ký tự' },
                        ]}
                    >
                        <Input autoComplete="name" />
                    </Form.Item>
                    <Form.Item
                        name="username"
                        label="Tên đăng nhập"
                        extra="Từ 3 đến 50 ký tự, gồm chữ không dấu, chữ số, dấu chấm, gạch dưới, gạch ngang."
                        rules={[
                            { required: true, message: 'Nhập tên đăng nhập' },
                            { min: 3, max: 50, message: 'Từ 3 đến 50 ký tự' },
                            { pattern: /^[A-Za-z0-9._-]+$/, message: 'Chỉ dùng chữ không dấu, chữ số, dấu chấm, gạch dưới, gạch ngang' },
                        ]}
                    >
                        <Input autoComplete="username" />
                    </Form.Item>
                    <Form.Item
                        name="password"
                        label="Mật khẩu"
                        extra="Từ 8 đến 72 ký tự."
                        rules={[
                            { required: true, message: 'Nhập mật khẩu' },
                            { min: 8, max: 72, message: 'Từ 8 đến 72 ký tự' },
                        ]}
                    >
                        <Input.Password autoComplete="new-password" />
                    </Form.Item>
                    {/* Chưa có chức năng lấy lại mật khẩu, nên bắt gõ lại để tránh gõ nhầm. */}
                    <Form.Item
                        name="confirmPassword"
                        label="Nhập lại mật khẩu"
                        dependencies={['password']}
                        rules={[
                            { required: true, message: 'Nhập lại mật khẩu' },
                            ({ getFieldValue }) => ({
                                validator: (_, value) => (!value || value === getFieldValue('password')
                                    ? Promise.resolve()
                                    : Promise.reject(new Error('Hai mật khẩu không khớp'))),
                            }),
                        ]}
                    >
                        <Input.Password autoComplete="new-password" />
                    </Form.Item>
                    {generalError && (
                        <Form.Item>
                            <Alert type="error" showIcon title={submit.error?.message} />
                        </Form.Item>
                    )}
                    <Button type="primary" htmlType="submit" block loading={submit.isPending}>Đăng ký</Button>
                </Form>
                <p className={styles.switch}>Đã có tài khoản? <Link to="/login">Đăng nhập</Link></p>
            </main>
        </div>
    );
}
