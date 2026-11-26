import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        strictPort: true,
        // Lúc phát triển, /api được chuyển sang backend nên trình duyệt thấy chung một địa chỉ, không cần CORS.
        proxy: {
            '/api': 'http://localhost:8080',
        },
    },
});
