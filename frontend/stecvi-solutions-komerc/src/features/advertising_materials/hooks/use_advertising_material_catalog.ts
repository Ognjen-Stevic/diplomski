import { useCallback, useEffect, useState } from "react";

import { advertisingMaterialApi } from "../api/advertising_material_api";
import type { AdvertisingMaterial } from "../types/advertising_material.types";

const sortByName = (materials: AdvertisingMaterial[]) =>
    [...materials].sort((left, right) =>
        left.name.localeCompare(right.name, "sr-Latn-RS"),
    );

export function useAdvertisingMaterialCatalog(enabled: boolean) {
    const [materials, setMaterials] = useState<AdvertisingMaterial[]>([]);
    const [loading, setLoading] = useState(enabled);
    const [error, setError] = useState("");

    const load = useCallback(async (signal?: AbortSignal) => {
        setLoading(true);
        setError("");
        try {
            const result = await advertisingMaterialApi.getAdvertisingMaterialCatalog(signal);
            setMaterials(sortByName(result));
        } catch (requestError) {
            if (requestError instanceof DOMException && requestError.name === "AbortError") return;
            setError(
                requestError instanceof Error
                    ? requestError.message
                    : "Reklamni materijal nije učitan.",
            );
        } finally {
            if (!signal?.aborted) setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (!enabled) return;
        const controller = new AbortController();
        void Promise.resolve().then(() => load(controller.signal));
        return () => controller.abort();
    }, [enabled, load]);

    const createAdvertisingMaterial = async (name: string) => {
        const normalizedName = name.trim();
        if (!normalizedName) throw new Error("Naziv reklamnog materijala je obavezan.");

        const created = await advertisingMaterialApi.createAdvertisingMaterial(normalizedName);
        setMaterials((current) => sortByName([...current, created]));
    };

    const setAdvertisingMaterialActive = async (
        material: AdvertisingMaterial,
        active: boolean,
    ) => {
        const updated = await advertisingMaterialApi.setAdvertisingMaterialActive(
            material.id,
            active,
        );
        setMaterials((current) => current.map((item) =>
            item.id === updated.id ? updated : item,
        ));
    };

    return {
        materials,
        loading,
        error,
        clearError: () => setError(""),
        reload: () => load(),
        createAdvertisingMaterial,
        setAdvertisingMaterialActive,
    };
}
