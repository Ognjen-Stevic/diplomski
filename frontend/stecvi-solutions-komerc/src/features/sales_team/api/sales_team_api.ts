import { apiRequest } from "../../../app/api/http_client";
import type {
    ManagersComparison,
    ManagerTeamSummary,
    TeamComparison,
    TeamMemberSummary,
} from "../types/sales_team.types";

const TEAM_SUMMARY_PATH = "/api/employees/team-summary";
const MANAGERS_OVERVIEW_PATH = "/api/employees/managers";
const TEAM_COMPARISON_PATH = "/api/sales-reports/team-comparison";
const MANAGERS_COMPARISON_PATH = "/api/sales-reports/managers-comparison";

const buildPath = (
    path: string,
    parameters: Record<string, number | null | undefined>,
) => {
    const search = new URLSearchParams();
    Object.entries(parameters).forEach(([name, value]) => {
        if (value !== null && value !== undefined) search.set(name, String(value));
    });
    const query = search.toString();
    return query ? `${path}?${query}` : path;
};

const normalizeComparisonMember = <T extends {
    bottlesSold: number;
    balloonsSold: number;
    gratisBottles: number;
    gratisBalloons: number;
    stimulation: number;
}>(member: T): T => ({
    ...member,
    bottlesSold: Number(member.bottlesSold),
    balloonsSold: Number(member.balloonsSold),
    gratisBottles: Number(member.gratisBottles),
    gratisBalloons: Number(member.gratisBalloons),
    stimulation: Number(member.stimulation),
});

export const salesTeamApi = {
    getTeamSummary(managerId?: number | null, signal?: AbortSignal) {
        return apiRequest<TeamMemberSummary[]>(
            buildPath(TEAM_SUMMARY_PATH, { managerId }),
            { signal },
        );
    },

    getManagersOverview(signal?: AbortSignal) {
        return apiRequest<ManagerTeamSummary[]>(MANAGERS_OVERVIEW_PATH, { signal });
    },

    async getTeamComparison(
        year: number,
        month: number,
        managerId?: number | null,
        signal?: AbortSignal,
    ): Promise<TeamComparison> {
        const result = await apiRequest<TeamComparison>(
            buildPath(TEAM_COMPARISON_PATH, { year, month, managerId }),
            { signal },
        );
        return {
            ...result,
            managerStimulation: Number(result.managerStimulation),
            salesRepresentatives: result.salesRepresentatives.map(normalizeComparisonMember),
        };
    },

    async getManagersComparison(
        year: number,
        month: number,
        signal?: AbortSignal,
    ): Promise<ManagersComparison> {
        const result = await apiRequest<ManagersComparison>(
            buildPath(MANAGERS_COMPARISON_PATH, { year, month }),
            { signal },
        );
        return {
            ...result,
            managers: result.managers.map(normalizeComparisonMember),
        };
    },
};
