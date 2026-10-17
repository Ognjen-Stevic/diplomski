import { createContext } from "react";

import type { Permission } from "../../roles/permissions";
import type { AuthUser } from "./types/auth_user";
import type { LoginRequest } from "./types/login_request";

export type AuthContextValue = {
    user: AuthUser | null;
    isLoading: boolean;
    isAuthenticated: boolean;
    login: (credentials: LoginRequest) => Promise<void>;
    logout: () => void;
    hasPermission: (permission: Permission) => boolean;
};

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
