import { useEffect, useState } from "react";

import { productApi } from "../../features/product/product_api";
import type { Product } from "../types/product";

export function useProducts(enabled = true) {
    const [products, setProducts] = useState<Product[]>([]);
    const [loading, setLoading] = useState(enabled);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!enabled) return;
        let active = true;
        productApi.getAvailableProducts()
            .then((result) => { if (active) setProducts(result); })
            .catch((requestError: unknown) => {
                if (active) setError(requestError instanceof Error ? requestError.message : "Proizvodi nisu učitani.");
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [enabled]);

    return { products, loading, error };
}
