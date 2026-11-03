import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { useCallback } from "react";

import { advertisingMaterialApi } from "../api/advertising_material_api";
import type { AdvertisingMaterial } from "../types/advertising_material.types";

const sortByName = (materials: AdvertisingMaterial[]) =>
    [...materials].sort((left, right) =>
        left.name.localeCompare(right.name, "sr-Latn-RS"),
    );

export function useAdvertisingMaterialCatalog(enabled: boolean) {
    const load = useCallback((signal: AbortSignal) => advertisingMaterialApi.getAdvertisingMaterialCatalog(signal), []);
    const query = useSessionRequest("material-catalog", load, enabled);
    const materials = query.data ?? [];
    const { loading, error } = query;
    const setMaterials = query.updateData;
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
        clearError: query.reload,
        reload: query.reload,
        createAdvertisingMaterial,
        setAdvertisingMaterialActive,
    };
}
