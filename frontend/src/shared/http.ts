import { getToken } from './token';

/** Lỗi theo từng field của form (README 6.6). */
export type FieldError = { field: string; message: string };

/**
 * Lỗi từ API. Xử lý theo `code`, không so sánh `message` (README 6.6); `message` hiển thị được cho người
 * dùng. `status = 0` là không gọi được máy chủ.
 */
export class ApiError extends Error {
    readonly status: number;
    readonly code: string;
    readonly errors: FieldError[];

    constructor(status: number, code: string, message: string, errors: FieldError[] = []) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.code = code;
        this.errors = errors;
    }
}

type ProblemDetail = { code?: string; detail?: string; errors?: FieldError[] };

let onUnauthenticated: (() => void) | null = null;

/** Hàm được gọi khi token không còn dùng được; AuthProvider dùng để về trang đăng nhập. */
export function setUnauthenticatedHandler(handler: (() => void) | null): void {
    onUnauthenticated = handler;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const token = getToken();
    const headers: Record<string, string> = {};
    if (token !== null) {
        headers['Authorization'] = `Bearer ${token}`;
    }
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }

    let response: Response;
    try {
        response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch {
        throw new ApiError(0, 'NETWORK_ERROR', 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.');
    }

    if (response.ok) {
        return (response.status === 204 ? undefined : await response.json()) as T;
    }

    const error = await toApiError(response);
    // So lại token để lỗi 401 đến muộn của lần đăng nhập trước không đăng xuất lần đăng nhập mới.
    if (error.code === 'UNAUTHENTICATED' && token !== null && token === getToken()) {
        onUnauthenticated?.();
    }
    throw error;
}

/** Backend luôn trả ProblemDetail; body khác (ví dụ lỗi của proxy) thì báo lỗi chung. */
async function toApiError(response: Response): Promise<ApiError> {
    const problem: ProblemDetail | null = await response.json().catch(() => null);
    if (problem?.code === undefined) {
        return new ApiError(response.status, 'UNKNOWN_ERROR', 'Máy chủ đang gặp sự cố. Thử lại sau ít phút.');
    }
    return new ApiError(response.status, problem.code, problem.detail ?? 'Đã có lỗi xảy ra.', problem.errors);
}

export const http = {
    get: <T>(path: string) => request<T>('GET', path),
    post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
    put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
    delete: <T>(path: string) => request<T>('DELETE', path),
};
