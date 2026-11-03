import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "../../auth/hooks/useAuth";
import { authApi } from "../../auth/auth_api";
import { PERMISSIONS } from "../../../roles/permissions";
import { requisitionApi } from "../requisition_api";

function useRequisitionQuery<T>(key: string, load: (signal: AbortSignal) => Promise<T>, enabled: boolean) {
    const { user } = useAuth();
    const token = authApi.getToken();
    const context = useMemo(() => ({ key, user, token, enabled }), [key, user, token, enabled]);
    const current = useRef<typeof context | null>(context);
    useLayoutEffect(() => {
        current.current = context;
        return () => { current.current = null; };
    }, [context]);
    const isCurrent = useCallback(() => current.current === context && authApi.getToken() === token, [context, token]);
    const [attempt, setAttempt] = useState(0);
    const identity = useMemo(() => ({ context, attempt }), [context, attempt]);
    const [result, setResult] = useState<{ identity: typeof identity; data: T | null; error: string } | null>(null);
    useEffect(() => {
        if (!enabled || !user) return;
        const controller = new AbortController();
        void (async () => {
            try {
                const data = await load(controller.signal);
                if (!controller.signal.aborted && isCurrent()) setResult({ identity, data, error: "" });
            } catch (reason) {
                if (!controller.signal.aborted && isCurrent()) setResult({ identity, data: null,
                    error: reason instanceof Error ? reason.message : "Trebovanja nisu učitana." });
            }
        })();
        return () => controller.abort();
    }, [enabled, identity, isCurrent, load, user]);
    const retry = useCallback(() => {
        if (isCurrent()) setAttempt((value) => value + 1);
    }, [isCurrent]);
    const matches = result?.identity === identity;
    return { data: matches ? result.data : null, error: matches ? result.error : "",
        loading: enabled && Boolean(user) && !matches, retry, context, identity, isCurrent };
}

export function useMonthlyRequisitions(year: number, month: number, customerId: number | null = null) {
    const { hasPermission } = useAuth();
    const load = useCallback((signal: AbortSignal) => customerId === null
        ? requisitionApi.getMonthlyRequisition(year, month, signal)
        : requisitionApi.getMonthlyCustomerRequisitions(customerId, year, month, signal), [year, month, customerId]);
    return useRequisitionQuery(`monthly:${year}:${month}:${customerId}`, load,
        hasPermission(PERMISSIONS.REQUISITIONS_READ) && (customerId === null || Number.isSafeInteger(customerId) && customerId > 0));
}

export function useRequisition(id: number, enabled = true) {
    const { hasPermission } = useAuth();
    const load = useCallback((signal: AbortSignal) => requisitionApi.findById(id, signal), [id]);
    return useRequisitionQuery(`detail:${id}`, load,
        enabled && hasPermission(PERMISSIONS.REQUISITIONS_READ) && Number.isSafeInteger(id) && id > 0);
}

export function useRequisitionMutationScope() {
    const { user } = useAuth();
    const token = authApi.getToken();
    const scope = useMemo(() => ({ user, token }), [user, token]);
    const current = useRef<typeof scope | null>(scope);
    useLayoutEffect(() => {
        current.current = scope;
        return () => { current.current = null; };
    }, [scope]);
    return useCallback(() => current.current === scope && authApi.getToken() === token, [scope, token]);
}
