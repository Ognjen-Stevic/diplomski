import { apiRequest } from "../../app/api/http_client";
import type { GoodsRequestRecord, ProcessGoodsRequestStatus } from "./types/goods_request.types";

export type CreateGoodsRequest = {
    items: Array<{
        productCode?: string;
        materialId?: number;
        quantity: number;
    }>;
    note: string | null;
};

export const goodsRequestApi = {
    create(payload: CreateGoodsRequest): Promise<GoodsRequestRecord> {
        return apiRequest<GoodsRequestRecord>("/api/goods-requests", {
            method: "POST",
            body: JSON.stringify(payload),
        });
    },

    getMonthlyGoodsRequests(year: number, month: number, signal?: AbortSignal) {
        return apiRequest<GoodsRequestRecord[]>(
            `/api/goods-requests?year=${year}&month=${month}`,
            { signal },
        );
    },

    updateStatus(goodsRequestId: number, status: ProcessGoodsRequestStatus) {
        return apiRequest<GoodsRequestRecord>(`/api/goods-requests/${goodsRequestId}/status`, {
            method: "PATCH",
            body: JSON.stringify({ status }),
        });
    },
};
