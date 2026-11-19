import { useCallback } from "react";

import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { reportApi } from "../../report/report_api";
import { formatInteger, formatLiters, formatRsd } from "../../report/utils/sales_report.utils";
import { requisitionApi } from "../../requisition/requisition_api";

export type MonthlyDashboardStatsMode = "sales" | "bookkeeper" | "ceo";

export type DashboardStatItem = {
    id: string;
    value: string;
    label: string;
};

async function loadStats(
    mode: MonthlyDashboardStatsMode,
    year: number,
    month: number,
    signal: AbortSignal,
): Promise<DashboardStatItem[]> {
    if (mode === "bookkeeper") {
        const statistics = await requisitionApi.getDashboardMonthlyStatistics(year, month, signal);

        return [
            {
                id: "cancelled-requisitions",
                value: formatInteger(statistics.cancelledCount),
                label: "Otkazano ovog meseca",
            },
            {
                id: "completed-requisitions",
                value: formatInteger(statistics.completedCount),
                label: "Završena trebovanja ovog meseca",
            },
        ];
    }

    if (mode === "ceo") {
        const [statistics, report] = await Promise.all([
            requisitionApi.getDashboardMonthlyStatistics(year, month, signal),
            reportApi.getMonthlyReport(year, month, null, null, signal),
        ]);
        return [
            {
                id: "completed-requisitions",
                value: formatInteger(statistics.completedCount),
                label: "Ukupno završenih trebovanja ovog meseca",
            },
            {
                id: "liters",
                value: formatLiters(report.metrics.totalLiters),
                label: "Ukupno prodato litara ovog meseca",
            },
            {
                id: "packages",
                value: `${formatInteger(report.metrics.totalBottles)} / ${formatInteger(report.metrics.totalJugs)}`,
                label: "Prodato flaša / balona",
            },
            {
                id: "incentive",
                value: formatRsd(report.metrics.totalIncentive),
                label: "Ukupna stimulacija ovog meseca",
            },
        ];
    }

    const [statistics, report] = await Promise.all([
        requisitionApi.getDashboardMonthlyStatistics(year, month, signal),
        reportApi.getMonthlyReport(year, month, null, null, signal),
    ]);

    return [
        {
            id: "requisitions",
            value: formatInteger(statistics.completedCount),
            label: "Trebovanja ovog meseca",
        },
        {
            id: "gratis-requisitions",
            value: formatInteger(statistics.completedGratisCount),
            label: "Gratis trebovanja ovog meseca",
        },
        {
            id: "incentive",
            value: formatRsd(report.metrics.totalIncentive),
            label: "Trenutna stimulacija",
        },
        {
            id: "packages",
            value: `${formatInteger(report.metrics.totalBottles)} / ${formatInteger(report.metrics.totalJugs)}`,
            label: "Prodato boca / balona",
        },
    ];
}

export function useMonthlyDashboardStats(mode: MonthlyDashboardStatsMode) {
    const currentDate = new Date();
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth() + 1;
    const load = useCallback(
        (signal: AbortSignal) => loadStats(mode, year, month, signal),
        [mode, month, year],
    );
    const query = useSessionRequest(
        `dashboard-stats:${mode}:${year}:${month}`,
        load,
        { errorMessage: "Podaci za ovaj mesec nisu učitani." },
    );

    return {
        stats: query.data ?? [],
        loading: query.loading,
        error: query.error,
        retry: query.reload,
    };
}
