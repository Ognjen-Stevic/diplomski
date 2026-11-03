import { useCallback } from "react";
import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { productApi } from "../product_api";
export function useProducts(enabled = true) {
    const load = useCallback((signal: AbortSignal) => productApi.getAvailableProducts(signal), []);
    const query = useSessionRequest("available-products", load, enabled);
    return { products: query.data ?? [], loading: query.loading, error: query.error, retry: query.reload };
}
