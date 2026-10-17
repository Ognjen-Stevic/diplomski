import type { MonthlyRequestItemStatus } from "../../../shared/types/monthly_request";

export type GoodsRequestRecord = {
    id: number;
    salesRep: {
        id: number;
        firstName: string;
        lastName: string;
    };
    items: Array<{
        name: string;
        quantity: number;
    }>;
    note: string | null;
    createdAt: string;
    status: MonthlyRequestItemStatus;
    hasPendingItems: boolean;
};

export type ProcessGoodsRequestStatus = Exclude<MonthlyRequestItemStatus, "PENDING">;
