import type { GRATIS_REASONS } from "../data/requisition.data";

export type RequisitionType = "classic" | "gratis";

export type GratisReason = (typeof GRATIS_REASONS)[number] | "";

export type RequisitionStatus =
    | "draft"
    | "submitted"
    | "approved"
    | "rejected"
    | "rejected_by_bookkeeper"
    | "processing"
    | "completed"
    | "cancelled_by_manager"
    | "cancelled_by_bookkeeper";

export type RequisitionItem = {
    productCode: string;
    productName: string;
    quantity: number;
    price: number;
    pictureUrl: string;
    active: boolean;
};

export type RequisitionListItem = {
    revision: string;
    id: number;
    salesRep: {
        id: number;
        firstName: string;
        lastName: string;
        email: string;
    };
    buyerId: number;
    buyerName: string;
    companyName: string;
    buyerEmail: string | null;
    type: RequisitionType;
    itemCount: number;
    totalQuantity: number;
    items: RequisitionItem[];
    gratisReason: "LISTING" | "CONSUMPTION" | "GIFT" | "OTHER" | null;
    customReason: string | null;
    note: string | null;
    createdAt: string;
    status: RequisitionStatus;
    statusNote: string | null;
    actions: {
        canEdit: boolean;
        allowedStatusTransitions: RequisitionStatus[];
    };
};
