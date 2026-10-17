import type { RequisitionStatus } from "../types/requisition.types";

export const GRATIS_REASONS = [
    "Za ulistavanje",
    "Za potrošnju",
    "Poklon",
    "Drugo",
] as const;

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
