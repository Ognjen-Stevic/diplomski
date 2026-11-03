import { useAuth } from "../../auth/hooks/useAuth";
import { useCallback, useEffect, useMemo, useState } from "react";

import { salesTeamApi } from "../api/sales_team_api";
import type { TeamSummaryMember } from "../types/sales_team.types";

type TeamSummaryMode = "team" | "managers";

type UseTeamSummaryOptions = {
    enabled: boolean;
    mode: TeamSummaryMode;
    managerId?: number | null;
};

export function useTeamSummary({
    enabled,
    mode,
    managerId = null,
}: UseTeamSummaryOptions) {
    const [members, setMembers] = useState<TeamSummaryMember[]>([]);
    const [loading, setLoading] = useState(enabled);
    const [error, setError] = useState("");
    const { sessionId } = useAuth();
    const [retryKey, setRetryKey] = useState(0);
    const requestKey = `${enabled}:${mode}:${managerId ?? "self"}:${retryKey}`;
    const identity = useMemo(() => ({ requestKey, sessionId }), [requestKey, sessionId]);
    const [stateKey, setStateKey] = useState(identity);

    useEffect(() => {
        const controller = new AbortController();

        void Promise.resolve().then(async () => {
            if (controller.signal.aborted) return;

            setStateKey(identity);
            setMembers([]);
            setError("");
            if (!enabled) {
                setLoading(false);
                return;
            }
            setLoading(true);

            try {
                const result = mode === "managers"
                    ? await salesTeamApi.getManagersOverview(controller.signal)
                    : await salesTeamApi.getTeamSummary(managerId, controller.signal);
                if (!controller.signal.aborted) setMembers(result);
            } catch (requestError) {
                if (controller.signal.aborted) return;
                setError(
                    requestError instanceof Error
                        ? requestError.message
                        : "Članovi tima nisu učitani.",
                );
            } finally {
                if (!controller.signal.aborted) setLoading(false);
            }
        });

        return () => controller.abort();
    }, [identity, enabled, managerId, mode, requestKey, retryKey]);

    const retry = useCallback(() => setRetryKey((current) => current + 1), []);

    const stateMatchesRequest = stateKey === identity;

    return {
        members: stateMatchesRequest ? members : [],
        loading: enabled && (!stateMatchesRequest || loading),
        error: stateMatchesRequest ? error : "",
        retry,
    };
}
