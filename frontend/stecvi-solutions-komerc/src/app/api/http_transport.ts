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

export async function fetchFromApi(
    path: string,
    init: RequestInit = {},
): Promise<Response> {
    try {
        return await fetch(`${API_URL}${path}`, init);
    } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") throw error;
        throw new Error("Nije moguće povezati se sa serverom.", { cause: error });
    }
}

export async function readApiError(
    response: Response,
    fallbackMessage: string,
): Promise<HttpError> {
    let message = fallbackMessage;

    try {
        const body = await response.json() as ApiErrorBody;
        message = body.message ?? body.detail ?? message;
    } catch {
        // Keep the caller's fallback for empty or non-JSON responses.
    }

    return new HttpError(message, response.status);
}
