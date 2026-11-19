import { apiRequest } from "../../app/api/http_client";
import type { ManagedProduct, ProductCreatePayload, ProductPage, ProductStatusFilter } from "./types/product_management.types";

export const productManagementApi = {
    findAll: (
        page: number,
        search: string,
        status: ProductStatusFilter,
        signal?: AbortSignal,
    ) => {
        const parameters = new URLSearchParams({ page: String(page), status });
        if (search.trim()) parameters.set("search", search.trim());
        return apiRequest<ProductPage>(`/api/products?${parameters}`, { signal });
    },
    create: (payload: ProductCreatePayload, image: File | null) => {
        const formData = new FormData();
        formData.append("product", new Blob([JSON.stringify(payload)], { type: "application/json" }));
        if (image) formData.append("image", image);
        return apiRequest<ManagedProduct>("/api/products", { method: "POST", body: formData });
    },
    deactivate: (productId: number) => apiRequest<ManagedProduct>(
        `/api/products/${productId}/deactivate`,
        { method: "PATCH" },
    ),
};
