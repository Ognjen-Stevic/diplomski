import { useCallback } from "react";
import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { advertisingMaterialApi } from "../api/advertising_material_api";
export function useAvailableAdvertisingMaterials(enabled = true) {

    const load = useCallback((signal: AbortSignal) => advertisingMaterialApi.getAvailableAdvertisingMaterials(signal), []);
    const query = useSessionRequest("available-materials", load, enabled);
    return { materials: query.data ?? [], loading: query.loading, error: query.error, retry: query.reload };
}
