import { createBrowserRouter, Navigate } from 'react-router';
import { LoginPage } from './features/auth/LoginPage';
import { RegisterPage } from './features/auth/RegisterPage';
import { RequireRole } from './features/auth/RequireRole';
import { NotReadyPage } from './shared/components/NotReadyPage';
import { StudentLayout } from './shared/layouts/StudentLayout';
import { TeacherLayout } from './shared/layouts/TeacherLayout';

export const router = createBrowserRouter([
    { path: '/login', element: <LoginPage /> },
    { path: '/register', element: <RegisterPage /> },
    {
        path: '/teacher',
        element: <RequireRole role="TEACHER" />,
        children: [
            {
                element: <TeacherLayout />,
                children: [
                    { index: true, element: <Navigate to="sessions" replace /> },
                    { path: 'questions', element: <NotReadyPage title="Ngân hàng câu hỏi" /> },
                    { path: 'exams', element: <NotReadyPage title="Đề thi" /> },
                    { path: 'sessions', element: <NotReadyPage title="Kỳ thi" /> },
                ],
            },
        ],
    },
    {
        path: '/student',
        element: <RequireRole role="STUDENT" />,
        children: [
            {
                element: <StudentLayout />,
                children: [{ index: true, element: <NotReadyPage title="Kỳ thi của tôi" /> }],
            },
        ],
    },
    { path: '*', element: <Navigate to="/login" replace /> },
]);
