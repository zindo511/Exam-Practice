import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App, ConfigProvider } from 'antd';
import viVN from 'antd/locale/vi_VN';
import { RouterProvider } from 'react-router/dom';
import '@fontsource/be-vietnam-pro/400.css';
import '@fontsource/be-vietnam-pro/500.css';
import '@fontsource/be-vietnam-pro/600.css';
import '@fontsource/be-vietnam-pro/700.css';
import './global.css';
import { AuthProvider } from './features/auth/AuthContext';
import { router } from './router';
import { ApiError } from './shared/http';
import { theme } from './theme';

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            // Lỗi 4xx gọi lại cũng vậy; lỗi mạng và lỗi server thì thử lại 2 lần.
            retry: (failures, error) =>
                !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failures < 2,
        },
    },
});

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <ConfigProvider theme={theme} locale={viVN}>
            <App>
                <QueryClientProvider client={queryClient}>
                    <AuthProvider>
                        <RouterProvider router={router} />
                    </AuthProvider>
                </QueryClientProvider>
            </App>
        </ConfigProvider>
    </StrictMode>,
);
