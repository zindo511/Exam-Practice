import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { setUnauthenticatedHandler } from '../../shared/http';
import { clearToken, getToken, saveToken } from '../../shared/token';
import { getCurrentUser, login as loginApi, type CurrentUser } from './api';

const CURRENT_USER = ['current-user'];

type Auth = {
    /** `null` khi chưa đăng nhập, hoặc đã có token nhưng chưa tải xong thông tin người dùng. */
    user: CurrentUser | null;
    hasToken: boolean;
    /** Lỗi khi tải thông tin người dùng lúc mở lại trang. */
    error: Error | null;
    retry: () => void;
    login: (username: string, password: string) => Promise<void>;
    logout: () => void;
};

const AuthContext = createContext<Auth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const queryClient = useQueryClient();
    const [hasToken, setHasToken] = useState(() => getToken() !== null);
    const { data, error, refetch } = useQuery({
        queryKey: CURRENT_USER,
        queryFn: getCurrentUser,
        enabled: hasToken,
        staleTime: Infinity,
    });

    const logout = useCallback(() => {
        clearToken();
        setHasToken(false);
        queryClient.clear();
    }, [queryClient]);

    /** Tải xong thông tin người dùng mới coi là đã đăng nhập; lỗi giữa chừng thì bỏ token. */
    const login = useCallback(async (username: string, password: string) => {
        const { accessToken } = await loginApi(username, password);
        saveToken(accessToken);
        try {
            queryClient.setQueryData(CURRENT_USER, await getCurrentUser());
        } catch (e) {
            clearToken();
            throw e;
        }
        setHasToken(true);
    }, [queryClient]);

    useEffect(() => {
        setUnauthenticatedHandler(logout);
        return () => setUnauthenticatedHandler(null);
    }, [logout]);

    const auth = useMemo<Auth>(() => ({
        user: hasToken ? data ?? null : null,
        hasToken,
        error,
        retry: () => void refetch(),
        login,
        logout,
    }), [hasToken, data, error, refetch, login, logout]);

    return <AuthContext value={auth}>{children}</AuthContext>;
}

export function useAuth(): Auth {
    const auth = useContext(AuthContext);
    if (auth === null) {
        throw new Error('useAuth phải nằm trong AuthProvider');
    }
    return auth;
}
