import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { ApiError, http, setUnauthenticatedHandler } from './http';
import { clearToken, saveToken } from './token';

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('sessionStorage', {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => store.set(key, value),
        removeItem: (key: string) => store.delete(key),
    });
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    fetchMock.mockReset();
    setUnauthenticatedHandler(null);
    vi.unstubAllGlobals();
});

function problem(status: number, body: object): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/problem+json' } });
}

async function failure(call: Promise<unknown>): Promise<ApiError> {
    const error = await call.then(() => null, (e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    return error as ApiError;
}

test('gửi token khi đã đăng nhập', async () => {
    saveToken('abc');
    fetchMock.mockResolvedValue(Response.json({ id: 1 }));

    expect(await http.get('/api/users/me')).toEqual({ id: 1 });
    expect(fetchMock.mock.calls[0]?.[1]?.headers).toEqual({ Authorization: 'Bearer abc' });
});

test('không gửi token khi chưa đăng nhập; body là JSON', async () => {
    fetchMock.mockResolvedValue(Response.json({}));

    await http.post('/api/auth/login', { username: 'an' });

    expect(fetchMock.mock.calls[0]?.[1]?.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(fetchMock.mock.calls[0]?.[1]?.body).toBe('{"username":"an"}');
});

test('204 không có body', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    expect(await http.post('/api/x')).toBeUndefined();
});

test('ProblemDetail thành ApiError có code và lỗi từng field', async () => {
    fetchMock.mockResolvedValue(problem(400, {
        code: 'VALIDATION_FAILED', detail: 'Dữ liệu không hợp lệ', errors: [{ field: 'username', message: 'Trống' }],
    }));

    const error = await failure(http.post('/api/x', {}));

    expect(error).toMatchObject({ status: 400, code: 'VALIDATION_FAILED', message: 'Dữ liệu không hợp lệ' });
    expect(error.errors).toEqual([{ field: 'username', message: 'Trống' }]);
});

test('body không phải ProblemDetail thì báo lỗi chung', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 500 }));

    expect((await failure(http.get('/api/x'))).code).toBe('UNKNOWN_ERROR');
});

test('không gọi được máy chủ', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    expect(await failure(http.get('/api/x'))).toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
});

test('token hết hạn thì gọi handler', async () => {
    saveToken('cu');
    const handler = vi.fn();
    setUnauthenticatedHandler(handler);
    fetchMock.mockResolvedValue(problem(401, { code: 'UNAUTHENTICATED', detail: 'Bạn cần đăng nhập' }));

    await failure(http.get('/api/x'));

    expect(handler).toHaveBeenCalledOnce();
});

test('sai mật khẩu không phải hết hạn token', async () => {
    saveToken('cu');
    const handler = vi.fn();
    setUnauthenticatedHandler(handler);
    fetchMock.mockResolvedValue(problem(401, { code: 'INVALID_CREDENTIALS', detail: 'Sai' }));

    await failure(http.post('/api/auth/login', {}));

    expect(handler).not.toHaveBeenCalled();
});

test('lỗi 401 đến muộn của token cũ không đăng xuất lần đăng nhập mới', async () => {
    saveToken('cu');
    const handler = vi.fn();
    setUnauthenticatedHandler(handler);
    fetchMock.mockImplementation(async () => {
        clearToken();
        saveToken('moi');
        return problem(401, { code: 'UNAUTHENTICATED', detail: 'Bạn cần đăng nhập' });
    });

    await failure(http.get('/api/x'));

    expect(handler).not.toHaveBeenCalled();
});
