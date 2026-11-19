import { authApi } from "../auth/auth_api";
import { refreshDashboardAlerts } from "../dashboard/alert_api";
import { apiRequest, downloadAuthenticatedFile } from "../../app/api/http_client";
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

type ApiRequisitionSummary = {
    id: number;
    salesRepId: number;
    salesRepFirstName: string;
    salesRepLastName: string;
    salesRepEmail: string;
    buyerId: number;
    buyerObjectName: string;
    buyerCompanyName: string;
    buyerEmail: string | null;
    type: "NORMAL" | "GRATIS";
    createdAt: string;
    status: ApiRequisitionStatus;
};

type ApiRequisitionPage = {
    totalCount: number;
    totalElements: number;
    gratisCount: number;
    normalCount: number;
    totalPages: number;
    pageNumber: number;
    content: ApiRequisitionSummary[];
};

type ApiRequisitionSummaryPage = {
    totalElements: number;
    totalPages: number;
    pageNumber: number;
    content: ApiRequisitionSummary[];
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

export type MonthlyRequisitionPage = {
    totalCount: number;
    totalElements: number;
    gratisCount: number;
    normalCount: number;
    totalPages: number;
    pageNumber: number;
    requisitions: RequisitionListItem[];
};

export type RequisitionSummaryPage = {
    totalElements: number;
    totalPages: number;
    pageNumber: number;
    requisitions: RequisitionListItem[];
};

export type DashboardMonthlyStatistics = {
    completedCount: number;
    completedGratisCount: number;
    cancelledCount: number;
};

async function mutate<T>(path: string, init: RequestInit): Promise<T> {
    const token = authApi.getToken();
    const result = await apiRequest<T>(path, init);
    if (authApi.getToken() === token) refreshDashboardAlerts();
    return result;
}

async function downloadPdf(requisitionId: number, signal?: AbortSignal): Promise<void> {
    return downloadAuthenticatedFile(
        `/api/requisitions/${requisitionId}/pdf`,
        `trebovanje-${requisitionId}.pdf`,
        "PDF trebovanja nije preuzet.",
        signal,
    );
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

function toRequisitionSummary(requisition: ApiRequisitionSummary): RequisitionListItem {
    return {
        id: requisition.id,
        revision: "",
        salesRep: {
            id: requisition.salesRepId,
            firstName: requisition.salesRepFirstName,
            lastName: requisition.salesRepLastName,
            email: requisition.salesRepEmail,
        },
        buyerId: requisition.buyerId,
        buyerName: requisition.buyerObjectName,
        companyName: requisition.buyerCompanyName,
        buyerEmail: requisition.buyerEmail,
        type: requisition.type === "GRATIS" ? "gratis" : "classic",
        itemCount: 0,
        totalQuantity: 0,
        items: [],
        gratisReason: null,
        customReason: null,
        note: null,
        createdAt: requisition.createdAt,
        status: requisition.status.toLowerCase() as RequisitionListItem["status"],
        statusNote: null,
        actions: { canEdit: false, allowedStatusTransitions: [] },
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
    async getMonthlyRequisitionPage(
        year: number,
        month: number,
        page: number,
        search: string,
        status: RequisitionListItem["status"] | "ALL",
        signal?: AbortSignal,
    ): Promise<MonthlyRequisitionPage> {
        const params = new URLSearchParams({
            year: String(year),
            month: String(month),
            page: String(page - 1),
        });
        const normalizedSearch = search.trim();
        if (normalizedSearch) params.set("search", normalizedSearch);
        if (status !== "ALL") params.set("status", status.toUpperCase());
        const result = await apiRequest<ApiRequisitionPage>(
            `/api/requisitions/page?${params.toString()}`,
            { signal },
        );
        return {
            ...result,
            pageNumber: result.pageNumber + 1,
            requisitions: result.content.map(toRequisitionSummary),
        };
    },
    getDashboardMonthlyStatistics(
        year: number,
        month: number,
        signal?: AbortSignal,
    ): Promise<DashboardMonthlyStatistics> {
        return apiRequest<DashboardMonthlyStatistics>(
            `/api/requisitions/dashboard-statistics?year=${year}&month=${month}`,
            { signal },
        );
    },
    async getMonthlyCustomerRequisitionPage(
        customerId: number,
        year: number,
        month: number,
        page: number,
        signal?: AbortSignal,
    ): Promise<RequisitionSummaryPage> {
        const result = await apiRequest<ApiRequisitionSummaryPage>(
            `/api/requisitions/customer/${customerId}/page?year=${year}&month=${month}&page=${page - 1}`,
            { signal },
        );
        return {
            ...result,
            pageNumber: result.pageNumber + 1,
            requisitions: result.content.map(toRequisitionSummary),
        };
    },
};
