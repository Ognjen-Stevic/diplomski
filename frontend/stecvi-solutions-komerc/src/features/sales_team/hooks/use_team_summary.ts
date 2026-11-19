import { useCallback } from "react";

import { useSessionRequest } from "../../../shared/hooks/use_session_request";
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
    const load = useCallback(
        async (signal: AbortSignal): Promise<TeamSummaryMember[]> => mode === "managers"
            ? await salesTeamApi.getManagersOverview(signal)
            : await salesTeamApi.getTeamSummary(managerId, signal),
        [managerId, mode],
    );
    const query = useSessionRequest(
        `team-summary:${mode}:${managerId ?? "self"}`,
        load,
        { enabled, errorMessage: "Članovi tima nisu učitani." },
    );

    return {
        members: query.data ?? [],
        loading: query.loading,
        error: query.error,
        retry: query.reload,
    };
}
