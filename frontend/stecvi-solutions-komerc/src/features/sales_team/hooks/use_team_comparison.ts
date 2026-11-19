import { useCallback } from "react";

import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { salesTeamApi } from "../api/sales_team_api";
import type { ManagersComparison, TeamComparison } from "../types/sales_team.types";

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
    const load = useCallback(
        async (signal: AbortSignal): Promise<TeamComparison | ManagersComparison> => mode === "managers"
            ? await salesTeamApi.getManagersComparison(year, month, signal)
            : await salesTeamApi.getTeamComparison(year, month, managerId, signal),
        [managerId, mode, month, year],
    );
    const query = useSessionRequest(
        `team-comparison:${mode}:${year}:${month}:${managerId ?? "self"}`,
        load,
        { enabled, errorMessage: "Poređenje tima nije učitano." },
    );

    return {
        comparison: query.data,
        loading: query.loading,
        error: query.error,
        retry: query.reload,
    };
}
