import type { RequisitionStatus } from "../types/requisition.types";

export const GRATIS_REASONS = ["LISTING", "CONSUMPTION", "GIFT", "OTHER"] as const;
export const GRATIS_REASON_LABELS = {
    LISTING: "Za ulistavanje",
    CONSUMPTION: "Za potro\u0161nju",
    GIFT: "Poklon",
    OTHER: "Drugo",
} as const;

export const REQUISITION_STATUS_LABELS: Record<RequisitionStatus, string> = {
    draft: "Nacrt",
    submitted: "Poslato menadžeru",
    approved: "Odobreno",
    rejected: "Odbijeno",
    rejected_by_bookkeeper: "Odbio knjigovođa",
    processing: "U obradi",
    completed: "Završeno",
    cancelled_by_manager: "Otkazao menadžer",
    cancelled_by_bookkeeper: "Otkazao knjigovođa",
};
