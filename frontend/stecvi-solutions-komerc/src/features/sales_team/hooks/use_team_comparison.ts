import { useAuth } from "../../auth/hooks/useAuth";
import { useCallback, useEffect, useMemo, useState } from "react";

import { salesTeamApi } from "../api/sales_team_api";
import type {
    ManagersComparison,
    TeamComparison,
} from "../types/sales_team.types";

type TeamComparisonMode = "sales-reps" | "managers";

type UseTeamComparisonOptions = {
    enabled: boolean;
    mode: TeamComparisonMode;
    year: number;
    month: number;
    managerId?: number | null;
};

export function useTeamComparison({
    enabled,
    mode,
    year,
    month,
    managerId = null,
}: UseTeamComparisonOptions) {
    const [comparison, setComparison] = useState<TeamComparison | ManagersComparison | null>(null);
    const [loading, setLoading] = useState(enabled);
    const [error, setError] = useState("");
    const { sessionId } = useAuth();
    const [retryKey, setRetryKey] = useState(0);
    const requestKey = `${enabled}:${mode}:${year}:${month}:${managerId ?? "self"}:${retryKey}`;
    const identity = useMemo(() => ({ requestKey, sessionId }), [requestKey, sessionId]);
    const [stateKey, setStateKey] = useState(identity);

    useEffect(() => {
        const controller = new AbortController();

        void Promise.resolve().then(async () => {
            if (controller.signal.aborted) return;

            setStateKey(identity);
            setComparison(null);
            setError("");
            if (!enabled) {
                setLoading(false);
                return;
            }
            setLoading(true);

            try {
                const result = mode === "managers"
                    ? await salesTeamApi.getManagersComparison(year, month, controller.signal)
                    : await salesTeamApi.getTeamComparison(
                        year,
                        month,
                        managerId,
                        controller.signal,
                    );
                if (!controller.signal.aborted) setComparison(result);
            } catch (requestError) {
                if (controller.signal.aborted) return;
                setError(
                    requestError instanceof Error
                        ? requestError.message
                        : "Poređenje tima nije učitano.",
                );
            } finally {
                if (!controller.signal.aborted) setLoading(false);
            }
        });

        return () => controller.abort();
    }, [identity, enabled, managerId, mode, month, requestKey, retryKey, year]);

    const retry = useCallback(() => setRetryKey((current) => current + 1), []);

    const stateMatchesRequest = stateKey === identity;

    return {
        comparison: stateMatchesRequest ? comparison : null,
        loading: enabled && (!stateMatchesRequest || loading),
        error: stateMatchesRequest ? error : "",
        retry,
    };
}
