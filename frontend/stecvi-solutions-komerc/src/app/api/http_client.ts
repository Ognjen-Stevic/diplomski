import { authApi } from "../../features/auth/auth_api";

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, "") ?? "";

type ApiErrorBody = {
    message?: string;
    detail?: string;
};

export class HttpError extends Error {
    constructor(
        message: string,
        public readonly status: number,
    ) {
        super(message);
        this.name = "HttpError";
    }
}

export async function authenticatedFetch(
    path: string,
    init: RequestInit = {},
): Promise<Response> {
    const requestToken = authApi.getToken();
    let response: Response;

    try {
        response = await fetch(`${API_URL}${path}`, {
            ...init,
            headers: {
                ...(requestToken ? { Authorization: `Bearer ${requestToken}` } : {}),
                ...init.headers,
            },
        });
    } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") throw error;
        throw new Error("Nije moguće povezati se sa serverom.", { cause: error });
    }

    if (response.status === 401 && requestToken) {
        authApi.invalidateSession(requestToken);
    }

    return response;
}

export async function apiRequest<T>(
    path: string,
    init: RequestInit = {},
): Promise<T> {
    const response = await authenticatedFetch(path, {
        ...init,
        headers: {
            ...(init.body && !(init.body instanceof FormData)
                ? { "Content-Type": "application/json" }
                : {}),
            ...init.headers,
        },
    });

    if (!response.ok) {
        let message = response.status === 401
            ? "Sesija je istekla. Prijavite se ponovo."
            : response.status === 403
                ? "Nemate dozvolu za ovu akciju."
                : "Zahtev nije uspeo.";

        try {
            const body = await response.json() as ApiErrorBody;
            message = body.message ?? body.detail ?? message;
        } catch {
            // Keep the status-specific fallback for empty or non-JSON responses.
        }

        throw new HttpError(message, response.status);
    }

    if (response.status === 204 || response.headers.get("content-length") === "0") {
        return undefined as T;
    }

    return response.json() as Promise<T>;
}
