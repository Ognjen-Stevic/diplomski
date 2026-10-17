import { apiRequest } from "../../../app/api/http_client";
import type {
    AdvertisingMaterial,
    AvailableAdvertisingMaterial,
} from "../types/advertising_material.types";

const BASE_PATH = "/api/advertising-materials";

export const advertisingMaterialApi = {
    getAvailableAdvertisingMaterials: (signal?: AbortSignal) =>
        apiRequest<AvailableAdvertisingMaterial[]>(`${BASE_PATH}/available`, { signal }),

    getAdvertisingMaterialCatalog: (signal?: AbortSignal) =>
        apiRequest<AdvertisingMaterial[]>(BASE_PATH, { signal }),

    createAdvertisingMaterial: (name: string) =>
        apiRequest<AdvertisingMaterial>(BASE_PATH, {
            method: "POST",
            body: JSON.stringify({ name }),
        }),

    setAdvertisingMaterialActive: (materialId: number, active: boolean) =>
        apiRequest<AdvertisingMaterial>(`${BASE_PATH}/${materialId}/active`, {
            method: "PATCH",
            body: JSON.stringify({ active }),
        }),
};
