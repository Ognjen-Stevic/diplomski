import { PERMISSIONS, type Permission } from "../../roles/permissions";
import type { Role } from "../../roles/role.types";
import type { AuthUser } from "./types/auth_user";
import type { LoginRequest } from "./types/login_request";

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, "") ?? "";
const TOKEN_KEY = "stecvi_access_token";

type SessionInvalidationListener = () => void;

const sessionInvalidationListeners = new Set<SessionInvalidationListener>();

type ApiUser = {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
    role: string;
    permissions: string[];
};

type AuthResponse = {
    accessToken: string;
    tokenType: string;
    expiresIn: number;
    user: ApiUser;
};

export class AuthApiError extends Error {
    constructor(message: string, public readonly status: number) {
        super(message);
        this.name = "AuthApiError";
    }
}

function normalizeRole(apiRole: string): Role {
    const role = apiRole.replace(/^ROLE_/, "").toUpperCase();

    if (
        role === "SALES_REP"
        || role === "MANAGER"
        || role === "CEO"
        || role === "BOOKKEEPER"
    ) {
        return role;
    }

    throw new Error(`Unsupported user role: ${apiRole}`);
}

const knownPermissions = new Set<string>(Object.values(PERMISSIONS));

function isPermission(permission: string): permission is Permission {
    return knownPermissions.has(permission);
}

function toAuthUser(user: ApiUser): AuthUser {
    const role = normalizeRole(user.role);

    return {
        id: user.id,
        email: user.email,
        name: `${user.firstName} ${user.lastName}`.trim(),
        role,
        permissions: user.permissions.filter(isPermission),
    };
}

async function parseResponse<T>(response: Response): Promise<T> {
    if (response.ok) return response.json() as Promise<T>;

    let message = "Došlo je do greške. Pokušajte ponovo.";
    try {
        const body = await response.json() as { message?: string };
        if (body.message) message = body.message;
    } catch {
        // The fallback message is used for non-JSON error responses.
    }
    throw new AuthApiError(message, response.status);
}

async function authenticate(payload: LoginRequest) {
    const response = await fetch(`${API_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });
    const auth = await parseResponse<AuthResponse>(response);
    localStorage.setItem(TOKEN_KEY, auth.accessToken);
    return toAuthUser(auth.user);
}

function invalidateSession(token: string): boolean {
    if (localStorage.getItem(TOKEN_KEY) !== token) return false;

    localStorage.removeItem(TOKEN_KEY);
    sessionInvalidationListeners.forEach((listener) => listener());
    return true;
}

function logout(): void {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) invalidateSession(token);
}

function onSessionInvalidated(listener: SessionInvalidationListener): () => void {
    sessionInvalidationListeners.add(listener);
    return () => {
        sessionInvalidationListeners.delete(listener);
    };
}

export const authApi = {
    getToken: () => localStorage.getItem(TOKEN_KEY),
    login: (payload: LoginRequest) => authenticate(payload),
    invalidateSession,
    logout,
    onSessionInvalidated,
    async me(token: string) {
        const response = await fetch(`${API_URL}/api/auth/me`, {
            headers: { Authorization: `Bearer ${token}` },
        });
        return toAuthUser(await parseResponse<ApiUser>(response));
    },
};
