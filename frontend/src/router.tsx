import { createBrowserRouter, Navigate } from 'react-router';
import { LoginPage } from './features/auth/LoginPage';
import { RegisterPage } from './features/auth/RegisterPage';
import { RequireRole } from './features/auth/RequireRole';
import { ExamEditorPage } from './features/exams/ExamEditorPage';
import { ExamListPage } from './features/exams/ExamListPage';
import { QuestionListPage } from './features/questions/QuestionListPage';
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
                    { path: 'questions', element: <QuestionListPage /> },
                    { path: 'exams', element: <ExamListPage /> },
                    { path: 'exams/:examId', element: <ExamEditorPage /> },
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
