import { useCallback, useMemo } from "react";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

import { useSessionPolling } from "../../../../shared/hooks/use_session_polling";
import { useAuth } from "../../../auth/hooks/useAuth";
import { alertApi, DASHBOARD_ALERTS_REFRESH_EVENT } from "../../alert_api";
import {
    dashboardAlertDefinitions,
    type DashboardAlertCounters,
} from "../../types/alert.types";

const ALERT_REFRESH_INTERVAL_MS = 45_000;
const EMPTY_ALERTS: DashboardAlertCounters = {
    draftCustomers: 0,
    requisitionAlerts: 0,
    pendingGoodsRequests: 0,
};

export function DashboardAlerts() {
    const { user } = useAuth();
    const userRole = user?.role ?? null;
    const shouldPoll = userRole === "SALES_REP"
        || userRole === "MANAGER"
        || userRole === "BOOKKEEPER";
    const load = useCallback(
        (signal: AbortSignal) => alertApi.findAll(signal),
        [],
    );
    const query = useSessionPolling("dashboard-alerts", load, {
        enabled: shouldPoll,
        intervalMs: ALERT_REFRESH_INTERVAL_MS,
        refreshEvent: DASHBOARD_ALERTS_REFRESH_EVENT,
    });
    const counters = query.data ?? EMPTY_ALERTS;

    const visibleAlerts = useMemo(() => {
        if (!user || !shouldPoll) return [];

        return dashboardAlertDefinitions.filter((alert) =>
            alert.roles.includes(user.role) && counters[alert.key] > 0);
    }, [counters, shouldPoll, user]);

    if (visibleAlerts.length === 0) return null;

    return (
        <section
            aria-label="Obaveštenja"
            className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-3"
        >
            {visibleAlerts.map((alert) => {
                const content = <><span aria-hidden="true">🔔 </span>{alert.label}: {counters[alert.key]}</>;
                return (
                    <Link
                        key={`${alert.key}-${alert.label}`}
                        to={alert.route}
                        className="group flex min-h-24 items-center justify-between gap-4 rounded-2xl border border-stormy-teal/30 bg-white px-5 py-4 font-semibold text-yale-blue shadow-sm transition hover:-translate-y-0.5 hover:border-stormy-teal hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-stormy-teal focus-visible:ring-offset-2"
                    >
                        <span>{content}</span>
                        <ChevronRight
                            aria-hidden="true"
                            className="h-5 w-5 shrink-0 text-stormy-teal transition group-hover:translate-x-1"
                        />
                    </Link>
                );
            })}
        </section>
    );
}
