import { useEffect, useState } from "react";

import { advertisingMaterialApi } from "../api/advertising_material_api";
import type { AvailableAdvertisingMaterial } from "../types/advertising_material.types";

export function useAvailableAdvertisingMaterials(enabled = true) {
    const [materials, setMaterials] = useState<AvailableAdvertisingMaterial[]>([]);
    const [loading, setLoading] = useState(enabled);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!enabled) return;

        const controller = new AbortController();
        advertisingMaterialApi.getAvailableAdvertisingMaterials(controller.signal)
            .then(setMaterials)
            .catch((requestError: unknown) => {
                if (requestError instanceof DOMException && requestError.name === "AbortError") return;
                setError(
                    requestError instanceof Error
                        ? requestError.message
                        : "Reklamni materijal nije učitan.",
                );
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });

        return () => controller.abort();
    }, [enabled]);

    return { materials, loading: enabled && loading, error };
}
