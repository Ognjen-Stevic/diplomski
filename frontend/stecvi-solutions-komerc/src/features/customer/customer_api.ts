import { authenticatedFetch } from "../../app/api/http_client";
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

function toCustomer(customer: ApiCustomer): Customer {
    return {
        ...customer,
        phone: customer.phone ?? "",
        email: customer.email ?? "",
        googleMapsUrl: customer.googleMapsUrl ?? "",
        note: customer.note ?? "",
        status: customer.status.toLowerCase() as CustomerStatus,
    };
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await authenticatedFetch(path, {
        ...init,
        headers: {
            ...(init?.body ? { "Content-Type": "application/json" } : {}),
            ...init?.headers,
        },
    });

    if (!response.ok) {
        let message = "Zahtev nije uspeo.";
        try {
            const body = await response.json() as { message?: string; detail?: string };
            message = body.message ?? body.detail ?? message;
        } catch {
            // Keep the fallback for non-JSON responses.
        }
        throw new Error(message);
    }

    return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}

export const customerApi = {
    async findAll(search = "", page = 0, size = 50): Promise<CustomerPage> {
        const params = new URLSearchParams({ page: String(page), size: String(size) });
        if (search.trim()) params.set("search", search.trim());
        const result = await request<ApiCustomerPage>(`/api/customers?${params}`);
        return { ...result, content: result.content.map(toCustomer) };
    },
    async findById(customerId: number) {
        return toCustomer(await request<ApiCustomer>(`/api/customers/${customerId}`));
    },
    async create(payload: CustomerPayload) {
        return toCustomer(await request<ApiCustomer>("/api/customers", {
            method: "POST",
            body: JSON.stringify(payload),
        }));
    },
    async update(customerId: number, payload: CustomerPayload) {
        return toCustomer(await request<ApiCustomer>(`/api/customers/${customerId}`, {
            method: "PUT",
            body: JSON.stringify(payload),
        }));
    },
    async updateStatus(customerId: number, status: CustomerStatus) {
        return toCustomer(await request<ApiCustomer>(`/api/customers/${customerId}/status`, {
            method: "PATCH",
            body: JSON.stringify({ status: status.toUpperCase() }),
        }));
    },
    findTeamSalesRepresentatives: () => request<SalesRepresentativeSummary[]>(
        "/api/customers/team-sales-representatives",
    ),
    async updateSalesRepresentative(customerId: number, salesRepId: number) {
        return toCustomer(await request<ApiCustomer>(
            `/api/customers/${customerId}/sales-representative`,
            {
                method: "PATCH",
                body: JSON.stringify({ salesRepId }),
            },
        ));
    },
};
