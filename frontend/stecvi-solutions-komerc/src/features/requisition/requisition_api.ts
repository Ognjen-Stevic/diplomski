import { authApi } from "../auth/auth_api";
import { refreshDashboardAlerts } from "../dashboard/alert_api";
import { apiRequest, authenticatedFetch } from "../../app/api/http_client";
import type { RequisitionListItem } from "./types/requisition.types";

type ApiRequisitionStatus =
    | "DRAFT"
    | "SUBMITTED"
    | "APPROVED"
    | "REJECTED"
    | "REJECTED_BY_BOOKKEEPER"
    | "PROCESSING"
    | "COMPLETED"
    | "CANCELLED_BY_MANAGER"
    | "CANCELLED_BY_BOOKKEEPER";

type ApiRequisition = {
    id: number;
    revision: string;
    salesRep: { id: number; firstName: string; lastName: string; email: string };
    buyer: { id: number; objectName: string; companyName: string; email: string | null };
    type: "NORMAL" | "GRATIS";
    items: Array<{
        productCode: string;
        productName: string;
        quantity: number;
        price: number;
        pictureUrl: string;
        active: boolean;
    }>;
    gratisReason: "LISTING" | "CONSUMPTION" | "GIFT" | "OTHER" | null;
    customReason: string | null;
    note: string | null;
    createdAt: string;
    status: ApiRequisitionStatus;
    statusNote: string | null;
    actions: {
        canEdit: boolean;
        allowedStatusTransitions: ApiRequisitionStatus[];
    };
};

type ApiMonthlyRequisition = {
    totalCount: number;
    gratisCount: number;
    normalCount: number;
    requisitions: ApiRequisition[];
};

export type CreateRequisitionRequest = {
    expectedRevision?: string;
    customerId: number;
    type: "NORMAL" | "GRATIS";
    gratisReason: ApiRequisition["gratisReason"];
    customReason: string | null;
    note: string | null;
    items: Array<{ productCode: string; quantity: number }>;
};

export type MonthlyRequisition = {
    totalCount: number;
    gratisCount: number;
    normalCount: number;
    requisitions: RequisitionListItem[];
};

async function mutate<T>(path: string, init: RequestInit): Promise<T> {
    const token = authApi.getToken();
    const result = await apiRequest<T>(path, init);
    if (authApi.getToken() === token) refreshDashboardAlerts();
    return result;
}

async function downloadPdf(requisitionId: number, signal?: AbortSignal): Promise<void> {
    const token = authApi.getToken();
    const response = await authenticatedFetch(`/api/requisitions/${requisitionId}/pdf`, { signal });
    if (!response.ok) {
        let message = "PDF trebovanja nije preuzet.";
        try {
            const body = await response.json() as { message?: string; detail?: string };
            message = body.message ?? body.detail ?? message;
        } catch { /* Use the fallback for non-JSON responses. */ }
        throw new Error(message);
    }

    const disposition = response.headers.get("Content-Disposition");
    const encodedFilename = disposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
    const plainFilename = disposition?.match(/filename="?([^";]+)"?/i)?.[1];
    let filename = `trebovanje-${requisitionId}.pdf`;
    if (encodedFilename) {
        try {
            filename = decodeURIComponent(encodedFilename);
        } catch {
            filename = encodedFilename;
        }
    } else if (plainFilename) {
        filename = plainFilename;
    }

    const blob = await response.blob();
    signal?.throwIfAborted();
    if (authApi.getToken() !== token) throw new DOMException("Session changed", "AbortError");
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    try {
        link.href = objectUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
    } finally {
        link.remove();
        URL.revokeObjectURL(objectUrl);
    }
}

function toRequisition(requisition: ApiRequisition): RequisitionListItem {
    const totalQuantity = requisition.items.reduce((total, item) => total + Number(item.quantity), 0);
    return {
        id: requisition.id,
        revision: requisition.revision,
        salesRep: requisition.salesRep,
        buyerId: requisition.buyer.id,
        buyerName: requisition.buyer.objectName,
        companyName: requisition.buyer.companyName,
        buyerEmail: requisition.buyer.email,
        type: requisition.type === "GRATIS" ? "gratis" : "classic",
        itemCount: requisition.items.length,
        totalQuantity,
        items: requisition.items.map((item) => ({ ...item, quantity: Number(item.quantity) })),
        gratisReason: requisition.gratisReason,
        customReason: requisition.customReason,
        note: requisition.note,
        createdAt: requisition.createdAt,
        status: requisition.status.toLowerCase() as RequisitionListItem["status"],
        statusNote: requisition.statusNote,
        actions: {
            canEdit: requisition.actions.canEdit,
            allowedStatusTransitions: requisition.actions.allowedStatusTransitions.map(
                (status) => status.toLowerCase() as RequisitionListItem["status"],
            ),
        },
    };
}

export const requisitionApi = {
    async create(payload: CreateRequisitionRequest) {
        return mutate<ApiRequisition>("/api/requisitions", { method: "POST", body: JSON.stringify(payload) });
    },
    async findById(requisitionId: number, signal?: AbortSignal): Promise<RequisitionListItem> {
        return toRequisition(await apiRequest<ApiRequisition>(`/api/requisitions/${requisitionId}`, { signal }));
    },
    async update(requisitionId: number, payload: CreateRequisitionRequest): Promise<RequisitionListItem> {
        return toRequisition(await mutate<ApiRequisition>(`/api/requisitions/${requisitionId}`, {
            method: "PUT",
            body: JSON.stringify(payload),
        }));
    },
    async updateStatus(
        requisitionId: number,
        status: RequisitionListItem["status"],
        note: string | null,
        expectedRevision?: string,
    ): Promise<RequisitionListItem> {
        return toRequisition(await mutate<ApiRequisition>(`/api/requisitions/${requisitionId}/status`, {
            method: "PATCH",
            body: JSON.stringify({ status: status.toUpperCase(), note, expectedRevision }),
        }));
    },
    downloadPdf,
    async sendPdfByEmail(requisitionId: number, recipient: "SALES_REP" | "CUSTOMER"): Promise<void> {
        await apiRequest<void>(`/api/requisitions/${requisitionId}/email`, {
            method: "POST",
            body: JSON.stringify({ recipient }),
        });
    },
    async getMonthlyRequisition(
        year: number,
        month: number,
        signal?: AbortSignal,
    ): Promise<MonthlyRequisition> {
        const result = await apiRequest<ApiMonthlyRequisition>(
            `/api/requisitions?year=${year}&month=${month}`,
            { signal },
        );
        return { ...result, requisitions: result.requisitions.map(toRequisition) };
    },
    async getMonthlyCustomerRequisitions(customerId: number, year: number, month: number, signal?: AbortSignal): Promise<MonthlyRequisition> {
        const result = await apiRequest<ApiMonthlyRequisition>(
            `/api/requisitions/customer/${customerId}?year=${year}&month=${month}`,
            { signal },
        );
        return { ...result, requisitions: result.requisitions.map(toRequisition) };
    },
};
