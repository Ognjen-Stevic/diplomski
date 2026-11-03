import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { useAuth } from "../../../auth/hooks/useAuth";
import type { AuthUser } from "../../../auth/types/auth_user";
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

type AlertSnapshot = {
    user: AuthUser | null;
    counters: DashboardAlertCounters;
};

export function DashboardAlerts() {
    const { user } = useAuth();
    const [snapshot, setSnapshot] = useState<AlertSnapshot>({
        user: null,
        counters: EMPTY_ALERTS,
    });
    const userRole = user?.role ?? null;
    const shouldPoll = userRole === "SALES_REP"
        || userRole === "MANAGER"
        || userRole === "BOOKKEEPER";

    useEffect(() => {
        if (!user || !shouldPoll) return;

        let disposed = false;
        let timeoutId: number | undefined;
        let requestController: AbortController | undefined;
        let requestVersion = 0;

        const poll = async () => {
            const version = ++requestVersion;
            requestController = new AbortController();

            try {
                const counters = await alertApi.findAll(requestController.signal);
                if (!disposed) setSnapshot({ user, counters });
            } catch {
                // Alerts are non-blocking. Authenticated 401 responses still invalidate the session.
            } finally {
                if (!disposed && version === requestVersion) {
                    timeoutId = window.setTimeout(poll, ALERT_REFRESH_INTERVAL_MS);
                }
            }
        };

        void poll();
        const refreshImmediately = () => {
            requestVersion += 1;
            requestController?.abort();
            if (timeoutId !== undefined) window.clearTimeout(timeoutId);
            void poll();
        };
        window.addEventListener(DASHBOARD_ALERTS_REFRESH_EVENT, refreshImmediately);

        return () => {
            disposed = true;
            window.removeEventListener(DASHBOARD_ALERTS_REFRESH_EVENT, refreshImmediately);
            requestController?.abort();
            if (timeoutId !== undefined) window.clearTimeout(timeoutId);
        };
    }, [shouldPoll, user]);

    const visibleAlerts = useMemo(() => {
        if (!user || !shouldPoll || snapshot.user !== user) return [];

        return dashboardAlertDefinitions.filter((alert) =>
            alert.roles.includes(user.role) && snapshot.counters[alert.key] > 0);
    }, [shouldPoll, snapshot, user]);

    if (visibleAlerts.length === 0) return null;

    return (
        <aside
            aria-label="Obaveštenja"
            className="fixed bottom-24 right-4 z-40 flex w-[min(22rem,calc(100vw-2rem))] flex-col gap-3 lg:bottom-6 lg:right-6"
        >
            {visibleAlerts.map((alert) => {
                const content = <><span aria-hidden="true">🔔 </span>{alert.label}: {snapshot.counters[alert.key]}</>;
                const className = "rounded-2xl border border-stormy-teal/30 bg-white px-4 py-3 font-semibold text-yale-blue shadow-lg";
                return (
                    <Link key={`${alert.key}-${alert.label}`} to={alert.route} className={`${className} transition hover:border-stormy-teal hover:shadow-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-stormy-teal focus-visible:ring-offset-2`}>
                        {content}
                    </Link>
                );
            })}
        </aside>
    );
}
