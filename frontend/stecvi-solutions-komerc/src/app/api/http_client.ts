import { authApi } from "../../features/auth/auth_api";
import { fetchFromApi, readApiError } from "./http_transport";

export { HttpError } from "./http_transport";

export async function authenticatedFetch(
    path: string,
    init: RequestInit = {},
): Promise<Response> {
    const requestToken = authApi.getToken();
    const response = await fetchFromApi(path, {
        ...init,
        headers: {
            ...(requestToken ? { Authorization: `Bearer ${requestToken}` } : {}),
            ...init.headers,
        },
    });

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
        const message = response.status === 401
            ? "Sesija je istekla. Prijavite se ponovo."
            : response.status === 403
                ? "Nemate dozvolu za ovu akciju."
                : "Zahtev nije uspeo.";
        throw await readApiError(response, message);
    }

    if (response.status === 204 || response.headers.get("content-length") === "0") {
        return undefined as T;
    }

    return response.json() as Promise<T>;
}

function getDownloadFilename(contentDisposition: string | null): string | null {
    if (!contentDisposition) return null;

    const encodedFilename = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
    if (encodedFilename) {
        try {
            return decodeURIComponent(encodedFilename);
        } catch {
            return encodedFilename;
        }
    }

    return contentDisposition.match(/filename="?([^";]+)"?/i)?.[1] ?? null;
}

export async function downloadAuthenticatedFile(
    path: string,
    fallbackFilename: string,
    fallbackErrorMessage: string,
    signal?: AbortSignal,
): Promise<void> {
    const requestToken = authApi.getToken();
    const response = await authenticatedFetch(path, { signal });

    if (!response.ok) {
        throw await readApiError(response, fallbackErrorMessage);
    }

    const blob = await response.blob();
    signal?.throwIfAborted();
    if (authApi.getToken() !== requestToken) {
        throw new DOMException("Session changed", "AbortError");
    }

    const filename = getDownloadFilename(response.headers.get("Content-Disposition"))
        ?? fallbackFilename;
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");

    try {
        link.href = objectUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
    } finally {
        link.remove();
        URL.revokeObjectURL(objectUrl);
    }
}
