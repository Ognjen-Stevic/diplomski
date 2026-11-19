import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import type { ReportRequest } from "../types/sales_report.types";

export function useReportData<T>(
    request: ReportRequest,
    load: (signal: AbortSignal) => Promise<T>,
    onLoaded?: (data: T) => void,
) {
    const { type, year, month, salesRepId, managerId } = request;
    const key = `report:${type}:${year}:${month ?? "none"}:${salesRepId ?? "self"}:${managerId ?? "self"}`;
    const query = useSessionRequest(key, load, {
        errorMessage: "Izveštaj nije učitan.",
        onSuccess: onLoaded,
    });

    return {
        data: query.data,
        error: query.error,
        loading: query.loading,
        retry: query.reload,
        identity: query.identity,
    };
}
