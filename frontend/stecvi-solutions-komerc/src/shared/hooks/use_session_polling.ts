import { useEffect } from "react";

import { useSessionRequest } from "./use_session_request";

type SessionPollingOptions = {
    enabled?: boolean;
    errorMessage?: string;
    intervalMs: number;
    refreshEvent?: string;
};

export function useSessionPolling<T>(
    key: string,
    load: (signal: AbortSignal) => Promise<T>,
    {
        enabled = true,
        errorMessage,
        intervalMs,
        refreshEvent,
    }: SessionPollingOptions,
) {
    const query = useSessionRequest(key, load, {
        enabled,
        errorMessage,
        keepPreviousData: true,
    });
    const { loading, reload } = query;

    useEffect(() => {
        if (!enabled || loading) return;
        const timeoutId = window.setTimeout(reload, intervalMs);
        return () => window.clearTimeout(timeoutId);
    }, [enabled, intervalMs, loading, reload]);

    useEffect(() => {
        if (!enabled || !refreshEvent) return;
        const refresh = () => reload();
        window.addEventListener(refreshEvent, refresh);
        return () => window.removeEventListener(refreshEvent, refresh);
    }, [enabled, refreshEvent, reload]);

    return query;
}
