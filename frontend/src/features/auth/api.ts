import { http } from '../../shared/http';

export type Role = 'STUDENT' | 'TEACHER';

export type CurrentUser = {
    id: number;
    username: string;
    fullName: string;
    role: Role;
};

type TokenResponse = { accessToken: string; tokenType: string; expiresIn: number };

export type RegisterForm = {
    role: Role;
    fullName: string;
    username: string;
    password: string;
};

/** Tạo tài khoản; không đăng nhập luôn, phải gọi `login` sau. */
export function register(form: RegisterForm): Promise<CurrentUser> {
    return http.post('/api/auth/register', form);
}

export function login(username: string, password: string): Promise<TokenResponse> {
    return http.post('/api/auth/login', { username, password });
}

/** Người đang đăng nhập; token không chứa tên nên phải hỏi riêng. */
export function getCurrentUser(): Promise<CurrentUser> {
    return http.get('/api/users/me');
}
