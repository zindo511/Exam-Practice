import { Button, Flex, Layout } from 'antd';
import { Outlet } from 'react-router';
import { useAuth } from '../../features/auth/AuthContext';
import styles from './layouts.module.css';

/** Khung trang học sinh: một thanh trên cùng, không có menu. */
export function StudentLayout() {
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
                <Outlet />
            </Layout.Content>
        </Layout>
    );
}
