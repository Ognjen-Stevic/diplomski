import { authenticatedFetch } from "../../app/api/http_client";
import type { DashboardAlertCounters } from "./types/alert.types";

export const alertApi = {
    async findAll(signal?: AbortSignal): Promise<DashboardAlertCounters> {
        const response = await authenticatedFetch("/api/alerts", { signal });

        if (!response.ok) {
            throw new Error("Obaveštenja nisu učitana.");
        }

        return response.json() as Promise<DashboardAlertCounters>;
    },
};

export const DASHBOARD_ALERTS_REFRESH_EVENT = "dashboard-alerts:refresh";

export function refreshDashboardAlerts() {
    window.dispatchEvent(new Event(DASHBOARD_ALERTS_REFRESH_EVENT));
}
