const KEY = 'exam-practice.token';

/**
 * JWT nằm ở sessionStorage: đóng tab là mất, hợp với máy dùng chung ở trường (ADR-0020).
 */
export function getToken(): string | null {
    return sessionStorage.getItem(KEY);
}

export function saveToken(token: string): void {
    sessionStorage.setItem(KEY, token);
}

export function clearToken(): void {
    sessionStorage.removeItem(KEY);
}
