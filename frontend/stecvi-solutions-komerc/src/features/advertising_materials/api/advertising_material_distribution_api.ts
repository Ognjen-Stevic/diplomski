import { apiRequest } from "../../../app/api/http_client";
import type {
    AdvertisingMaterialDistribution,
    CreateAdvertisingMaterialDistributionRequest,
} from "../types/advertising_material_distribution.types";

const DISTRIBUTIONS_PATH = "/api/advertising-materials/distributions";

export const advertisingMaterialDistributionApi = {
    getMonthlyAdvertisingMaterials: (
        year: number,
        month: number,
        signal?: AbortSignal,
    ) => apiRequest<AdvertisingMaterialDistribution[]>(
        `${DISTRIBUTIONS_PATH}?year=${year}&month=${month}`,
        { signal },
    ),

    recordAdvertisingMaterialDistribution: (
        payload: CreateAdvertisingMaterialDistributionRequest,
    ) => apiRequest<AdvertisingMaterialDistribution[]>(DISTRIBUTIONS_PATH, {
        method: "POST",
        body: JSON.stringify(payload),
    }),
};
