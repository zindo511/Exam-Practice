import type { ReactNode } from 'react';
import { Button, Flex, Layout } from 'antd';
import { Outlet } from 'react-router';
import { useAuth } from '../../features/auth/AuthContext';
import styles from './layouts.module.css';

/**
 * Khung trang học sinh: một thanh trên cùng, không có menu. Dùng làm route cha (nội dung là `Outlet`), hoặc
 * bọc `children` cho trang tự chọn có thanh trên hay không, như trang bài làm.
 */
export function StudentLayout({ children }: { children?: ReactNode }) {
    const { user, logout } = useAuth();

    return (
        <Layout className={styles.page}>
            <Layout.Header className={styles.header}>
                <div className={styles.brand}>Exam Practice</div>
                <Flex align="center" gap={16}>
                    <span className={styles.userName}>{user?.fullName}</span>
                    <Button onClick={logout}>Đăng xuất</Button>
                </Flex>
            </Layout.Header>
            <Layout.Content className={styles.studentContent}>
                {children ?? <Outlet />}
            </Layout.Content>
        </Layout>
    );
}
