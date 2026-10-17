import { authenticatedFetch } from "../../app/api/http_client";
import type { ManagedProduct, ProductCreatePayload } from "./types/product_management.types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await authenticatedFetch(path, {
        ...init,
        headers: {
            ...(init?.body && !(init.body instanceof FormData)
                ? { "Content-Type": "application/json" }
                : {}),
            ...init?.headers,
        },
    });
    if (!response.ok) {
        const fallback = "Zahtev nije uspeo.";
        let message = fallback;
        try {
            const body = await response.json() as { message?: string; detail?: string };
            const reason = body.message ?? body.detail;
            if (reason) message = `${fallback} ${reason}`;
        } catch { /* Keep fallback for non-JSON responses. */ }
        throw new Error(message);
    }
    return response.json() as Promise<T>;
}

export const productManagementApi = {
    findAll: () => request<ManagedProduct[]>("/api/products"),
    create: (payload: ProductCreatePayload, image: File | null) => {
        const formData = new FormData();
        formData.append("product", new Blob([JSON.stringify(payload)], { type: "application/json" }));
        if (image) formData.append("image", image);
        return request<ManagedProduct>("/api/products", { method: "POST", body: formData });
    },
    deactivate: (productId: number) => request<ManagedProduct>(
        `/api/products/${productId}/deactivate`,
        { method: "PATCH" },
    ),
};
