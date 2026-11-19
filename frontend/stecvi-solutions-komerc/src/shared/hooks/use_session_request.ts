import {
    useCallback,
    useEffect,
    useLayoutEffect,
    useMemo,
    useRef,
    useState,
} from "react";

import { useAuth } from "../../features/auth/hooks/useAuth";

type SessionRequestOptions<T> = {
    enabled?: boolean;
    errorMessage?: string;
    keepPreviousData?: boolean;
    onSuccess?: (data: T) => void;
};

// Shared transport lifecycle only; feature hooks retain data mapping and user-facing messages.
export function useSessionRequest<T>(
    key: string,
    load: (signal: AbortSignal) => Promise<T>,
    enabledOrOptions: boolean | SessionRequestOptions<T> = true,
) {
    const options = typeof enabledOrOptions === "boolean"
        ? { enabled: enabledOrOptions }
        : enabledOrOptions;
    const enabled = options.enabled ?? true;
    const errorMessage = options.errorMessage ?? "Podaci nisu učitani.";
    const keepPreviousData = options.keepPreviousData ?? false;
    const onSuccessRef = useRef(options.onSuccess);
    useLayoutEffect(() => {
        onSuccessRef.current = options.onSuccess;
    }, [options.onSuccess]);

    const { sessionId, user } = useAuth();
    const context = useMemo(
        () => ({ key, sessionId, enabled }),
        [key, sessionId, enabled],
    );
    const active = useRef<object | null>(context);
    useLayoutEffect(() => {
        active.current = context;
        return () => {
            active.current = null;
        };
    }, [context]);
    const isCurrent = useCallback(() => active.current === context, [context]);
    const [attempt, setAttempt] = useState(0);
    const identity = useMemo(() => ({ context, attempt }), [context, attempt]);
    const [result, setResult] = useState<{
        context: object;
        identity: object;
        data: T | null;
        error: string;
    } | null>(null);

    useEffect(() => {
        if (!enabled || !user) return;
        const controller = new AbortController();

        void (async () => {
            try {
                const data = await load(controller.signal);
                if (!controller.signal.aborted && isCurrent()) {
                    setResult({ context, identity, data, error: "" });
                    onSuccessRef.current?.(data);
                }
            } catch (error) {
                if (!controller.signal.aborted && isCurrent()) {
                    setResult({
                        context,
                        identity,
                        data: null,
                        error: error instanceof Error ? error.message : errorMessage,
                    });
                }
            }
        })();

        return () => controller.abort();
    }, [context, enabled, errorMessage, identity, isCurrent, load, user]);

    const reload = useCallback(() => {
        if (isCurrent()) setAttempt((value) => value + 1);
    }, [isCurrent]);
    const updateData = useCallback(
        (update: (value: T) => T) => {
            if (isCurrent()) {
                setResult((previous) =>
                    previous?.context === context && previous.data !== null
                        ? { ...previous, data: update(previous.data) }
                        : previous,
                );
            }
        },
        [context, isCurrent],
    );

    const matches = result?.identity === identity && enabled && Boolean(user);
    const retained = keepPreviousData && result?.context === context && Boolean(user);

    return {
        data: matches || retained ? result?.data ?? null : null,
        loading: enabled && Boolean(user) && !matches,
        error: matches ? result?.error ?? "" : "",
        reload,
        retry: reload,
        isCurrent,
        context,
        identity,
        updateData,
    };
}

export function useSessionCurrent() {
    const { sessionId } = useAuth();
    const current = useRef<object | null>(sessionId);
    useLayoutEffect(() => {
        current.current = sessionId;
        return () => {
            current.current = null;
        };
    }, [sessionId]);
    return useCallback(() => current.current === sessionId, [sessionId]);
}
