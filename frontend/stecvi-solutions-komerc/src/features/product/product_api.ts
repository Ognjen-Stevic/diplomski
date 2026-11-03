import { apiRequest } from "../../app/api/http_client";
import type { Product } from "../../shared/types/product";

import { resolveProductPictureUrl } from "../../shared/utils/asset_url";

export const productApi = {
    async getAvailableProducts(signal?: AbortSignal): Promise<Product[]> {
        const products = await apiRequest<Product[]>("/api/products/available", { signal });
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
