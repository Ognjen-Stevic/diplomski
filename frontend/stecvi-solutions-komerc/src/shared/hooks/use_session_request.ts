import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useAuth } from "../../features/auth/hooks/useAuth";

// Small transport lifecycle shared by domain hooks; business operations stay in each feature.
export function useSessionRequest<T>(
  key: string,
  load: (signal: AbortSignal) => Promise<T>,
  enabled = true,
) {
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
        if (!controller.signal.aborted && isCurrent())
          setResult({ identity, data, error: "" });
      } catch (error) {
        if (!controller.signal.aborted && isCurrent())
          setResult({
            identity,
            data: null,
            error:
              error instanceof Error ? error.message : "Podaci nisu učitani.",
          });
      }
    })();
    return () => controller.abort();
  }, [enabled, identity, isCurrent, load, user]);
  const reload = useCallback(() => {
    if (isCurrent()) setAttempt((value) => value + 1);
  }, [isCurrent]);
  const updateData = useCallback(
    (update: (value: T) => T) => {
      if (isCurrent())
        setResult((previous) =>
          previous?.identity === identity && previous.data !== null
            ? { ...previous, data: update(previous.data) }
            : previous,
        );
    },
    [identity, isCurrent],
  );
  const matches = result?.identity === identity && enabled && Boolean(user);
  return {
    data: matches ? result.data : null,
    loading: enabled && Boolean(user) && !matches,
    error: matches ? result.error : "",
    reload,
    isCurrent,
    context,
    updateData,
  };
}

export function useSessionCurrent() {
    const { sessionId } = useAuth();
    const current = useRef<object | null>(sessionId);
    useLayoutEffect(() => { current.current = sessionId; return () => { current.current = null; }; }, [sessionId]);
    return useCallback(() => current.current === sessionId, [sessionId]);
}
