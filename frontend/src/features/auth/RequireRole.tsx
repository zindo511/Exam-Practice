import { Button, Flex, Result, Spin } from 'antd';
import { Navigate, Outlet } from 'react-router';
import type { Role } from './api';
import { useAuth } from './AuthContext';

/** Trang chủ của từng vai trò. */
export const HOME: Record<Role, string> = {
    STUDENT: '/student',
    TEACHER: '/teacher',
};

/** Chỉ cho vai trò `role` vào các trang con. Đây chỉ là điều hướng; quyền thật do backend kiểm tra. */
export function RequireRole({ role }: { role: Role }) {
    const { user, hasToken, error, retry, logout } = useAuth();

    if (!hasToken) {
        return <Navigate to="/login" replace />;
    }
    if (user === null && error !== null) {
        return (
            <Result
                status="warning"
                title={error.message}
                extra={[
                    <Button key="retry" type="primary" onClick={retry}>Thử lại</Button>,
                    <Button key="logout" onClick={logout}>Đăng xuất</Button>,
                ]}
            />
        );
    }
    if (user === null) {
        return (
            <Flex justify="center" align="center" style={{ minHeight: '100vh' }}>
                <Spin size="large" />
            </Flex>
        );
    }
    if (user.role !== role) {
        return <Navigate to={HOME[user.role]} replace />;
    }
    return <Outlet />;
}
