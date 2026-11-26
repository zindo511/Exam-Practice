import { Button, Flex, Layout, Menu } from 'antd';
import { Link, Outlet, useLocation } from 'react-router';
import { useAuth } from '../../features/auth/AuthContext';
import styles from './layouts.module.css';

const MENU = [
    { path: '/teacher/questions', label: 'Ngân hàng câu hỏi' },
    { path: '/teacher/exams', label: 'Đề thi' },
    { path: '/teacher/sessions', label: 'Kỳ thi' },
];

/** Khung trang giáo viên: menu bên trái; màn hẹp thì menu thu lại thành nút. */
export function TeacherLayout() {
    const { user, logout } = useAuth();
    const { pathname } = useLocation();
    const current = MENU.find((item) => pathname.startsWith(item.path));

    return (
        <Layout className={styles.page}>
            <Layout.Sider
                theme="light"
                width={230}
                breakpoint="md"
                collapsedWidth={0}
                // Nút mở menu ở màn hẹp; mặc định nằm thấp, đè lên tiêu đề trang.
                zeroWidthTriggerStyle={{ top: 12 }}
                className={styles.sider}
            >
                <div className={styles.siderBrand}>Exam Practice</div>
                <Menu
                    mode="inline"
                    className={styles.menu}
                    selectedKeys={current === undefined ? [] : [current.path]}
                    items={MENU.map((item) => ({ key: item.path, label: <Link to={item.path}>{item.label}</Link> }))}
                />
                <Flex vertical align="flex-start" gap={8} className={styles.siderUser}>
                    <span className={styles.userName}>{user?.fullName}</span>
                    <Button onClick={logout}>Đăng xuất</Button>
                </Flex>
            </Layout.Sider>
            <Layout.Content className={styles.teacherContent}>
                <Outlet />
            </Layout.Content>
        </Layout>
    );
}
