import { apiRequest } from "../../app/api/http_client";
import type {
    Customer,
    CustomerPayload,
    CustomerStatus,
    SalesRepresentativeSummary,
} from "./types/customer.types";

type ApiCustomer = {
    id: number;
    salesRep: {
        id: number;
        firstName: string;
        lastName: string;
    } | null;
    actions: {
        canCreateRequisition: boolean;
        canViewRequisitionHistory: boolean;
        canChangeSalesRep: boolean;
    };
    objectName: string;
    companyName: string;
    pib: string;
    phone: string | null;
    email: string | null;
    address: string;
    googleMapsUrl: string | null;
    note: string | null;
    status: "DRAFT" | "APPROVED" | "DEACTIVATED" | "DELETED";
};

export type CustomerPage = {
    content: Customer[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
};

type ApiCustomerPage = Omit<CustomerPage, "content"> & { content: ApiCustomer[] };

const STATUS_MAP: Record<ApiCustomer["status"], CustomerStatus> = { DRAFT: "draft", APPROVED: "approved", DEACTIVATED: "deactivated", DELETED: "deleted" };
function toCustomer(customer: ApiCustomer): Customer {
    if (!Object.hasOwn(STATUS_MAP, customer.status)) throw new Error("Nepoznat status kupca.");
    return {
        ...customer,
        phone: customer.phone ?? "",
        email: customer.email ?? "",
        googleMapsUrl: customer.googleMapsUrl ?? "",
        note: customer.note ?? "",
        status: STATUS_MAP[customer.status],
    };
}

export const customerApi = {
    async findAll(search = "", page = 0, size = 50, signal?: AbortSignal, risk?: string): Promise<CustomerPage> {
        const params = new URLSearchParams({ page: String(page), size: String(size) });
        if (search.trim()) params.set("search", search.trim());
        if (risk) params.set("risk", risk);
        const endpoint = risk ? "/api/ai/customers/catalog" : "/api/customers";
        const result = await apiRequest<ApiCustomerPage>(`${endpoint}?${params}`, { signal });
        return { ...result, content: result.content.map(toCustomer) };
    },
    async findById(customerId: number, signal?: AbortSignal) {
        return toCustomer(await apiRequest<ApiCustomer>(`/api/customers/${customerId}`, { signal }));
    },
    async create(payload: CustomerPayload) {
        return toCustomer(await apiRequest<ApiCustomer>("/api/customers", {
            method: "POST",
            body: JSON.stringify(payload),
        }));
    },
    async update(customerId: number, payload: CustomerPayload) {
        return toCustomer(await apiRequest<ApiCustomer>(`/api/customers/${customerId}`, {
            method: "PUT",
            body: JSON.stringify(payload),
        }));
    },
    async updateStatus(customerId: number, status: CustomerStatus) {
        return toCustomer(await apiRequest<ApiCustomer>(`/api/customers/${customerId}/status`, {
            method: "PATCH",
            body: JSON.stringify({ status: status.toUpperCase() }),
        }));
    },
    findTeamSalesRepresentatives: () => apiRequest<SalesRepresentativeSummary[]>(
        "/api/customers/team-sales-representatives",
    ),
    async updateSalesRepresentative(customerId: number, salesRepId: number) {
        return toCustomer(await apiRequest<ApiCustomer>(
            `/api/customers/${customerId}/sales-representative`,
            {
                method: "PATCH",
                body: JSON.stringify({ salesRepId }),
            },
        ));
    },
};
