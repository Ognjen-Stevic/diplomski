import { useCallback } from "react";
import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { productManagementApi } from "../product_management_api";
import type { ProductStatusFilter } from "../types/product_management.types";

export function useProductCatalog(
    page: number,
    search: string,
    status: ProductStatusFilter,
    enabled = true,
) {
    const load = useCallback(
        (signal: AbortSignal) => productManagementApi.findAll(page, search, status, signal),
        [page, search, status],
    );
    const query = useSessionRequest(
        `product-catalog:${page}:${search}:${status}`,
        load,
        enabled,
    );
    return {
        page: query.data,
        products: query.data?.content ?? [],
        loading: query.loading,
        error: query.error,
        retry: query.reload,
    };
}
