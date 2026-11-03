import type { CreateRequisitionRequest } from "../requisition_api";
import type { GratisReason, RequisitionType } from "../types/requisition.types";

export function requisitionPayload(input: {
    customerId: number; type: RequisitionType; reason: GratisReason; customReason: string;
    note: string | null; items: CreateRequisitionRequest["items"]; expectedRevision?: string;
}): CreateRequisitionRequest {
    return { customerId: input.customerId, type: input.type === "gratis" ? "GRATIS" : "NORMAL",
        gratisReason: input.type === "gratis" && input.reason ? input.reason : null,
        customReason: input.type === "gratis" && input.reason === "OTHER" ? input.customReason.trim() : null,
        note: input.note?.trim() || null, items: input.items, expectedRevision: input.expectedRevision };
}
