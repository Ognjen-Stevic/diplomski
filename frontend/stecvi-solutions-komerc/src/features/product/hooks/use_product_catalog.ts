import { useCallback } from "react";
import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { productManagementApi } from "../product_management_api";
export function useProductCatalog(enabled = true) {
    const load = useCallback((signal: AbortSignal) => productManagementApi.findAll(signal), []);
    const query = useSessionRequest("product-catalog", load, enabled);
    return { products: query.data ?? [], setProducts: query.updateData, loading: query.loading, error: query.error, retry: query.reload };
}
