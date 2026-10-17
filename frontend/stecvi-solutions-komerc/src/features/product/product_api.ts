import { authenticatedFetch } from "../../app/api/http_client";
import type { Product } from "../../shared/types/product";

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, "") ?? "";

export const productApi = {
    async getAvailableProducts(): Promise<Product[]> {
        const response = await authenticatedFetch("/api/products/available");
        if (!response.ok) throw new Error("Proizvodi nisu učitani.");
        const products = await response.json() as Product[];
        return products.map((product) => ({
            ...product,
            key: product.code,
            active: true,
            imageUrl: resolveProductPictureUrl(product.pictureUrl),
            details: `${product.code} · ${product.price.toLocaleString("sr-Latn-RS", {
                minimumFractionDigits: 2,
            })} RSD`,
        }));
    },
};

export function resolveProductPictureUrl(pictureUrl: string) {
    if (/^https?:\/\//i.test(pictureUrl)) return pictureUrl;
    return `${API_URL}/${pictureUrl.replace(/^\//, "")}`;
}
