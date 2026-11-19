import { useCallback } from "react";

import { PERMISSIONS } from "../../../roles/permissions";
import { useSessionCurrent, useSessionRequest } from "../../../shared/hooks/use_session_request";
import { useAuth } from "../../auth/hooks/useAuth";
import { requisitionApi } from "../requisition_api";
import type { RequisitionStatus } from "../types/requisition.types";

export function useMonthlyRequisitionPage(
    year: number,
    month: number,
    page: number,
    search: string,
    status: RequisitionStatus | "ALL",
) {
    const { hasPermission } = useAuth();
    const load = useCallback(
        (signal: AbortSignal) => requisitionApi.getMonthlyRequisitionPage(
            year, month, page, search, status, signal),
        [year, month, page, search, status],
    );

    return useSessionRequest(
        `monthly-page:${year}:${month}:${page}:${search}:${status}`,
        load,
        {
            enabled: hasPermission(PERMISSIONS.REQUISITIONS_READ),
            errorMessage: "Trebovanja nisu uÄitana.",
        },
    );
}

export function useMonthlyCustomerRequisitionPage(
    customerId: number,
    year: number,
    month: number,
    page: number,
) {
    const { hasPermission } = useAuth();
    const load = useCallback(
        (signal: AbortSignal) => requisitionApi.getMonthlyCustomerRequisitionPage(
            customerId, year, month, page, signal),
        [customerId, year, month, page],
    );
    return useSessionRequest(
        `customer-monthly-page:${customerId}:${year}:${month}:${page}`,
        load,
        {
            enabled: hasPermission(PERMISSIONS.REQUISITIONS_READ)
                && Number.isSafeInteger(customerId)
                && customerId > 0,
            errorMessage: "Trebovanja nisu uÄitana.",
        },
    );
}

export function useRequisition(id: number, enabled = true) {
    const { hasPermission } = useAuth();
    const load = useCallback(
        (signal: AbortSignal) => requisitionApi.findById(id, signal),
        [id],
    );

    return useSessionRequest(`detail:${id}`, load, {
        enabled: enabled
            && hasPermission(PERMISSIONS.REQUISITIONS_READ)
            && Number.isSafeInteger(id)
            && id > 0,
        errorMessage: "Trebovanje nije učitano.",
    });
}

export function useRequisitionMutationScope() {
    return useSessionCurrent();
}
