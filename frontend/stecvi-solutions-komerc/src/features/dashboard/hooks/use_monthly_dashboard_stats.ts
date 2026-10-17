import { useCallback, useEffect, useState } from "react";

import { useAuth } from "../../auth/hooks/useAuth";
import type { AuthUser } from "../../auth/types/auth_user";
import { reportApi } from "../../report/report_api";
import { formatInteger, formatLiters, formatRsd } from "../../report/utils/sales_report.utils";
import { requisitionApi, type MonthlyRequisition } from "../../requisition/requisition_api";
import { salesTeamApi } from "../../sales_team/api/sales_team_api";

export type MonthlyDashboardStatsMode = "sales" | "bookkeeper" | "ceo";

export type DashboardStatItem = {
    id: string;
    value: string;
    label: string;
};

type StatsState = {
    user: AuthUser | null;
    mode: MonthlyDashboardStatsMode;
    retryKey: number;
    stats: DashboardStatItem[];
    loading: boolean;
    error: string;
};

const INITIAL_STATE: StatsState = {
    user: null,
    mode: "sales",
    retryKey: -1,
    stats: [],
    loading: false,
    error: "",
};

const completedRequisitions = (monthly: MonthlyRequisition) =>
    monthly.requisitions.filter((requisition) => requisition.status === "completed");

const summaryValue = (
    summary: Awaited<ReturnType<typeof reportApi.getMonthlyReport>>["summary"],
    id: string,
) => summary.find((item) => item.id === id)?.value ?? 0;

async function loadStats(
    mode: MonthlyDashboardStatsMode,
    year: number,
    month: number,
    signal: AbortSignal,
): Promise<DashboardStatItem[]> {
    if (mode === "bookkeeper") {
        const requisitions = await requisitionApi.getMonthlyRequisition(year, month, signal);
        const cancelledCount = requisitions.requisitions.filter(
            (requisition) => requisition.status === "cancelled_by_manager"
                || requisition.status === "cancelled_by_bookkeeper",
        ).length;
        const completedCount = completedRequisitions(requisitions).length;

        return [
            {
                id: "cancelled-requisitions",
                value: formatInteger(cancelledCount),
                label: "Otkazano ovog meseca",
            },
            {
                id: "completed-requisitions",
                value: formatInteger(completedCount),
                label: "Završena trebovanja ovog meseca",
            },
        ];
    }

    if (mode === "ceo") {
        const [requisitions, report, managers] = await Promise.all([
            requisitionApi.getMonthlyRequisition(year, month, signal),
            reportApi.getMonthlyReport(year, month, null, null, signal),
            salesTeamApi.getManagersComparison(year, month, signal),
        ]);
        const completed = completedRequisitions(requisitions);
        const managerIncentive = managers.managers.reduce(
            (total, manager) => total + manager.stimulation,
            0,
        );

        return [
            {
                id: "completed-requisitions",
                value: formatInteger(completed.length),
                label: "Ukupno završenih trebovanja ovog meseca",
            },
            {
                id: "liters",
                value: formatLiters(summaryValue(report.summary, "liters")),
                label: "Ukupno prodato litara ovog meseca",
            },
            {
                id: "packages",
                value: `${formatInteger(summaryValue(report.summary, "bottles"))} / ${formatInteger(summaryValue(report.summary, "jugs"))}`,
                label: "Prodato flaša / balona",
            },
            {
                id: "incentive",
                value: formatRsd(summaryValue(report.summary, "incentive") + managerIncentive),
                label: "Ukupna stimulacija ovog meseca",
            },
        ];
    }

    const [requisitions, report] = await Promise.all([
        requisitionApi.getMonthlyRequisition(year, month, signal),
        reportApi.getMonthlyReport(year, month, null, null, signal),
    ]);
    const completed = completedRequisitions(requisitions);
    const completedGratisCount = completed.filter(
        (requisition) => requisition.type === "gratis",
    ).length;

    return [
        {
            id: "requisitions",
            value: formatInteger(completed.length),
            label: "Trebovanja ovog meseca",
        },
        {
            id: "gratis-requisitions",
            value: formatInteger(completedGratisCount),
            label: "Gratis trebovanja ovog meseca",
        },
        {
            id: "incentive",
            value: formatRsd(summaryValue(report.summary, "incentive")),
            label: "Trenutna stimulacija",
        },
        {
            id: "packages",
            value: `${formatInteger(summaryValue(report.summary, "bottles"))} / ${formatInteger(summaryValue(report.summary, "jugs"))}`,
            label: "Prodato boca / balona",
        },
    ];
}

export function useMonthlyDashboardStats(mode: MonthlyDashboardStatsMode) {
    const { user } = useAuth();
    const [state, setState] = useState<StatsState>(INITIAL_STATE);
    const [retryKey, setRetryKey] = useState(0);

    useEffect(() => {
        const controller = new AbortController();

        void Promise.resolve().then(async () => {
            if (controller.signal.aborted || !user) return;

            setState({
                user,
                mode,
                retryKey,
                stats: [],
                loading: true,
                error: "",
            });

            try {
                const currentDate = new Date();
                const stats = await loadStats(
                    mode,
                    currentDate.getFullYear(),
                    currentDate.getMonth() + 1,
                    controller.signal,
                );
                if (!controller.signal.aborted) {
                    setState({
                        user,
                        mode,
                        retryKey,
                        stats,
                        loading: false,
                        error: "",
                    });
                }
            } catch (requestError) {
                if (controller.signal.aborted) return;
                setState({
                    user,
                    mode,
                    retryKey,
                    stats: [],
                    loading: false,
                    error: requestError instanceof Error
                        ? requestError.message
                        : "Podaci za ovaj mesec nisu učitani.",
                });
            }
        });

        return () => controller.abort();
    }, [mode, retryKey, user]);

    const retry = useCallback(() => {
        setRetryKey((current) => current + 1);
    }, []);

    const stateMatchesRequest = state.user === user
        && state.mode === mode
        && state.retryKey === retryKey;

    return {
        stats: stateMatchesRequest ? state.stats : [],
        loading: user !== null && (!stateMatchesRequest || state.loading),
        error: stateMatchesRequest ? state.error : "",
        retry,
    };
}
