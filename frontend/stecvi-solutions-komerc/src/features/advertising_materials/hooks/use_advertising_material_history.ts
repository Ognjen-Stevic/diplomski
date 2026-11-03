import { useCallback } from "react";
import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { advertisingMaterialDistributionApi } from "../api/advertising_material_distribution_api";
export function useAdvertisingMaterialHistory(selectedDate: Date, enabled: boolean) {
    const year = selectedDate.getFullYear(); const month = selectedDate.getMonth() + 1;
    const load = useCallback((signal: AbortSignal) => advertisingMaterialDistributionApi.getMonthlyAdvertisingMaterials(year, month, signal), [year, month]);
    const query = useSessionRequest(`material-history:${year}:${month}`, load, enabled);
    return { records: query.data ?? [], loading: query.loading, error: query.error, retry: query.reload };
}
