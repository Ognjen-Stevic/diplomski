import { useCallback, useEffect, useMemo, useState } from "react";
import { authApi } from "../../auth/auth_api";
import { useAuth } from "../../auth/hooks/useAuth";
import type { ReportRequest } from "../types/sales_report.types";

export function useReportData<T>(
    request: ReportRequest,
    load: (signal: AbortSignal) => Promise<T>,
    onLoaded?: (data: T) => void,
) {
    const { user } = useAuth();
    const token = authApi.getToken();
    const [attempt, setAttempt] = useState(0);
    const { type, year, month, salesRepId, managerId } = request;
    // Object identity also distinguishes A → B → A and retries of the same query.
    const identity = useMemo(() => ({ user, token, type, year, month, salesRepId, managerId, attempt }),
        [user, token, type, year, month, salesRepId, managerId, attempt]);
    const [result, setResult] = useState<{
        identity: typeof identity;
        data: T | null;
        error: string;
    } | null>(null);

    useEffect(() => {
        if (!identity.user) return;
        const controller = new AbortController();
        const isCurrent = () => !controller.signal.aborted && authApi.getToken() === identity.token;
        void (async () => {
            try {
                const data = await load(controller.signal);
                if (!isCurrent()) return;
                setResult({ identity, data, error: "" });
                onLoaded?.(data);
            } catch (reason) {
                if (!isCurrent()) return;
                setResult({
                    identity,
                    data: null,
                    error: reason instanceof Error ? reason.message : "Izveštaj nije učitan.",
                });
            }
        })();
        return () => controller.abort();
    }, [identity, load, onLoaded]);

    const matches = result?.identity === identity;
    const retry = useCallback(() => setAttempt((current) => current + 1), []);
    return {
        data: matches ? result.data : null,
        error: matches ? result.error : "",
        loading: Boolean(user) && !matches,
        retry,
        identity,
    };
}
