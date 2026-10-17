import { NavLink } from "react-router-dom";

import { APP_ROUTES } from "../../../../app/routes";
import {
    useMonthlyDashboardStats,
    type DashboardStatItem,
    type MonthlyDashboardStatsMode,
} from "../../hooks/use_monthly_dashboard_stats";
import { StatCard } from "./stat_card";

type MonthlyStatsSectionProps = {
    mode?: MonthlyDashboardStatsMode;
    embedded?: boolean;
};

export function MonthlyStatsSection({ mode = "sales", embedded = false }: MonthlyStatsSectionProps) {
    const { stats, loading, error, retry } = useMonthlyDashboardStats(mode);
    const displayedStats = loading || stats.length === 0
        ? mode === "bookkeeper"
            ? BOOKKEEPER_LOADING_STATS
            : mode === "ceo" ? CEO_LOADING_STATS : SALES_LOADING_STATS
        : stats;

    if (embedded) {
        return (
            <>
                {displayedStats.map((stat) => (
                    <StatCard key={stat.id} value={stat.value} label={stat.label} />
                ))}
                {error && <StatsError error={error} onRetry={retry} embedded />}
            </>
        );
    }

    return (
        <section className="mb-8">
            <div className="mb-4 flex items-center justify-between gap-4">
                <h2 className="text-xl font-bold text-yale-blue">
                    {mode === "ceo" ? "Najvažniji podaci" : "Ovaj mesec"}
                </h2>
                <NavLink
                    to={mode === "bookkeeper" ? APP_ROUTES.requisitions : APP_ROUTES.reports}
                    className="text-sm font-semibold text-stormy-teal hover:text-yale-blue"
                >
                    {mode === "bookkeeper" ? "Sva trebovanja" : "Detaljan izveštaj"}
                </NavLink>
            </div>

            <div
                className={`grid grid-cols-2 gap-4 ${mode === "bookkeeper" ? "xl:max-w-2xl" : "xl:grid-cols-4"}`}
                aria-busy={loading}
            >
                {displayedStats.map((stat) => (
                    <StatCard key={stat.id} value={stat.value} label={stat.label} />
                ))}
            </div>

            {error && <StatsError error={error} onRetry={retry} />}
        </section>
    );
}

function StatsError({
    error,
    onRetry,
    embedded = false,
}: {
    error: string;
    onRetry: () => void;
    embedded?: boolean;
}) {
    return (
        <div
            role="alert"
            className={[
                "flex items-center justify-between gap-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700",
                embedded ? "col-span-full" : "mt-4",
            ].join(" ")}
        >
            <span>{error}</span>
            <button
                type="button"
                onClick={onRetry}
                className="shrink-0 rounded-lg border border-red-200 bg-white px-3 py-1.5 font-semibold transition hover:bg-red-100"
            >
                Pokušaj ponovo
            </button>
        </div>
    );
}

const BOOKKEEPER_LOADING_STATS: DashboardStatItem[] = [
    { id: "cancelled-requisitions", value: "—", label: "Otkazano ovog meseca" },
    { id: "completed-requisitions", value: "—", label: "Završena trebovanja ovog meseca" },
];

const CEO_LOADING_STATS: DashboardStatItem[] = [
    { id: "completed-requisitions", value: "—", label: "Ukupno završenih trebovanja ovog meseca" },
    { id: "liters", value: "—", label: "Ukupno prodato litara ovog meseca" },
    { id: "packages", value: "— / —", label: "Prodato flaša / balona" },
    { id: "incentive", value: "—", label: "Ukupna stimulacija ovog meseca" },
];

const SALES_LOADING_STATS: DashboardStatItem[] = [
    { id: "requisitions", value: "—", label: "Trebovanja ovog meseca" },
    { id: "gratis-requisitions", value: "—", label: "Gratis trebovanja ovog meseca" },
    { id: "incentive", value: "—", label: "Trenutna stimulacija" },
    { id: "packages", value: "— / —", label: "Prodato boca / balona" },
];
