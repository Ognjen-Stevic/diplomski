import { apiRequest } from "../../app/api/http_client";
import type { DashboardAlertCounters } from "./types/alert.types";

export const alertApi = {
    async findAll(signal?: AbortSignal): Promise<DashboardAlertCounters> {
        try {
            return await apiRequest<DashboardAlertCounters>("/api/alerts", { signal });
        } catch (error) {
            if (error instanceof DOMException && error.name === "AbortError") throw error;
            throw new Error("Obaveštenja nisu učitana.", { cause: error });
        }
    },
};

export const DASHBOARD_ALERTS_REFRESH_EVENT = "dashboard-alerts:refresh";

export function refreshDashboardAlerts() {
    window.dispatchEvent(new Event(DASHBOARD_ALERTS_REFRESH_EVENT));
}
