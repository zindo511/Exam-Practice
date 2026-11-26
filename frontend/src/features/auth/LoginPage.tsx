import { Alert, Button, Form, Input, Typography } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { Link, Navigate } from 'react-router';
import { useAuth } from './AuthContext';
import styles from './AuthPage.module.css';
import { HOME } from './RequireRole';

type LoginForm = { username: string; password: string };

export function LoginPage() {
    const { user, hasToken, login } = useAuth();
    const submit = useMutation({
        mutationFn: ({ username, password }: LoginForm) => login(username, password),
    });

    if (user !== null) {
        return <Navigate to={HOME[user.role]} replace />;
    }
    // Mở lại trang khi còn token: để RequireRole tải người dùng hoặc báo lỗi.
    if (hasToken) {
        return <Navigate to={HOME.STUDENT} replace />;
    }

    return (
        <div className={styles.page}>
            <div className={styles.brand}>Exam Practice</div>
            <main className={styles.card}>
                <Typography.Title level={1} className={styles.title}>Đăng nhập</Typography.Title>
                <Form<LoginForm> layout="vertical" size="large" requiredMark={false} onFinish={(form) => submit.mutate(form)}>
                    <Form.Item
                        name="username"
                        label="Tên đăng nhập"
                        rules={[{ required: true, whitespace: true, message: 'Nhập tên đăng nhập' }]}
                    >
                        <Input autoComplete="username" autoFocus />
                    </Form.Item>
                    <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Nhập mật khẩu' }]}>
                        <Input.Password autoComplete="current-password" />
                    </Form.Item>
                    {submit.error !== null && (
                        <Form.Item>
                            <Alert type="error" showIcon title={submit.error.message} />
                        </Form.Item>
                    )}
                    <Button type="primary" htmlType="submit" block loading={submit.isPending}>Đăng nhập</Button>
                </Form>
                <p className={styles.switch}>Chưa có tài khoản? <Link to="/register">Đăng ký</Link></p>
            </main>
        </div>
    );
}
