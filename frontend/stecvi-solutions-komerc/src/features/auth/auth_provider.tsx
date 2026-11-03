import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type PropsWithChildren,
} from "react";
import { authApi, AuthApiError } from "./auth_api";
import { AuthContext } from "./auth_context";
import type { AuthUser } from "./types/auth_user";
import type { LoginRequest } from "./types/login_request";
import type { Permission } from "../../roles/permissions";

export function AuthProvider({ children }: PropsWithChildren) {
  const token = useSyncExternalStore(
    authApi.subscribe,
    authApi.getToken,
    () => null,
  );
  const sessionId = useMemo(
    () => ({ credentialPresent: Boolean(token) }),
    [token],
  );
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{
    token: string | null;
    user: AuthUser | null;
    error: string;
    attempt: number;
  } | null>(null);
  const matches = state?.token === token && state?.attempt === attempt;
  const user = matches ? state.user : null;
  useEffect(() => {
    if (!token || (state?.token === token && state.attempt === attempt)) return;
    const controller = new AbortController();
    void authApi
      .me(token, controller.signal)
      .then((user) => {
        if (!controller.signal.aborted && authApi.getToken() === token)
          setState({ token, user, error: "", attempt });
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || authApi.getToken() !== token) return;
        if (error instanceof AuthApiError && error.status === 401)
          authApi.invalidateSession(token);
        else
          setState({
            token,
            user: null,
            error: "Sesiju nije moguće proveriti. Pokušajte ponovo.",
            attempt,
          });
      });
    return () => controller.abort();
  }, [token, attempt, state]);
  const retrySession = useCallback(() => setAttempt((value) => value + 1), []);
  const login = useCallback(
    async (credentials: LoginRequest) => {
      const user = await authApi.login(credentials);
      setState({ token: authApi.getToken(), user, error: "", attempt });
    },
    [attempt],
  );
  const logout = useCallback(() => authApi.logout(), []);
  const hasPermission = useCallback(
    (permission: Permission) => user?.permissions.includes(permission) ?? false,
    [user],
  );
  const value = useMemo(
    () => ({
      user,
      sessionId,
      restorationError: matches ? state.error : "",
      retrySession,
      isLoading: Boolean(token) && !matches,
      isAuthenticated: user !== null,
      login,
      logout,
      hasPermission,
    }),
    [
      user,
      sessionId,
      matches,
      state,
      retrySession,
      token,
      login,
      logout,
      hasPermission,
    ],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
