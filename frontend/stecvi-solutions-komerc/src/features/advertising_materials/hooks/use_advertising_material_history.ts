import { useEffect, useState } from "react";

import { advertisingMaterialDistributionApi } from "../api/advertising_material_distribution_api";
import type { AdvertisingMaterialDistribution } from "../types/advertising_material_distribution.types";

export function useAdvertisingMaterialHistory(
    selectedDate: Date,
    enabled: boolean,
) {
    const [records, setRecords] = useState<AdvertisingMaterialDistribution[]>([]);
    const [loading, setLoading] = useState(enabled);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!enabled) return;

        const controller = new AbortController();
        Promise.resolve().then(() => {
            if (controller.signal.aborted) return;
            setLoading(true);
            setError("");
            return advertisingMaterialDistributionApi.getMonthlyAdvertisingMaterials(
                selectedDate.getFullYear(),
                selectedDate.getMonth() + 1,
                controller.signal,
            ).then(setRecords);
        }).catch((requestError: unknown) => {
            if (requestError instanceof DOMException && requestError.name === "AbortError") return;
            setRecords([]);
            setError(
                requestError instanceof Error
                    ? requestError.message
                    : "Istorija reklamnog materijala nije učitana.",
            );
        }).finally(() => {
            if (!controller.signal.aborted) setLoading(false);
        });

        return () => controller.abort();
    }, [enabled, selectedDate]);

    return { records, loading, error };
}
