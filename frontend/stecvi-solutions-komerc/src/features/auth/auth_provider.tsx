import {
    useCallback,
    useEffect,
    useMemo,
    useState,
    type PropsWithChildren,
} from "react";

import type { AuthUser } from "./types/auth_user";
import type { Permission } from "../../roles/permissions";
import { AuthApiError, authApi } from "./auth_api";
import { AuthContext, type AuthContextValue } from "./auth_context";
import type { LoginRequest } from "./types/login_request";

export function AuthProvider({ children }: PropsWithChildren) {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [isLoading, setIsLoading] = useState(() => authApi.getToken() !== null);

    useEffect(() => {
        let active = true;
        const unsubscribe = authApi.onSessionInvalidated(() => {
            if (!active) return;
            setUser(null);
            setIsLoading(false);
        });

        const token = authApi.getToken();
        if (!token) {
            return () => {
                active = false;
                unsubscribe();
            };
        }

        authApi.me(token)
            .then((authenticatedUser) => {
                if (active && authApi.getToken() === token) {
                    setUser(authenticatedUser);
                }
            })
            .catch((error: unknown) => {
                if (active && error instanceof AuthApiError && error.status === 401) {
                    authApi.invalidateSession(token);
                }
            })
            .finally(() => {
                if (active) setIsLoading(false);
            });

        return () => {
            active = false;
            unsubscribe();
        };
    }, []);

    const login = useCallback(async (credentials: LoginRequest) => {
        setUser(await authApi.login(credentials));
        setIsLoading(false);
    }, []);

    const logout = useCallback(() => {
        authApi.logout();
        setUser(null);
    }, []);

    const hasPermission = useCallback(
        (permission: Permission): boolean =>
            user?.permissions.includes(permission) ?? false,
        [user],
    );

    const value = useMemo<AuthContextValue>(
        () => ({
            user,
            isLoading,
            isAuthenticated: user !== null,
            login,
            logout,
            hasPermission,
        }),
        [user, isLoading, login, logout, hasPermission],
    );

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
}
