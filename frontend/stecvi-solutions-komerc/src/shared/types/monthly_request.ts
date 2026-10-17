export type MonthlyRequestItemStatus = "PENDING" | "APPROVED" | "REJECTED";

export type MonthlyRequestRecord = {
    id: number;
    salesRep?: {
        firstName: string;
        lastName: string;
    };
    customer?: {
        objectName: string;
        companyName: string;
    };
    items: Array<{
        name: string;
        quantity: number;
    }>;
    note: string | null;
    date: string;
    status?: MonthlyRequestItemStatus;
};
