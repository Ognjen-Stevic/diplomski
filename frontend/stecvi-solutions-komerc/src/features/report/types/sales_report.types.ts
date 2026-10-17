export type ReportType = "monthly" | "ytd" | "gratis-monthly" | "gratis-ytd";

export type ReportSummary = {
    id: string;
    label: string;
    value: number;
    format: "integer" | "liters" | "currency";
    description?: string;
    tone: "blue" | "teal" | "amber";
};

export type ProductReportRow = {
    productCode: string;
    productName: string;
    pictureUrl: string;
    units: number;
    volumeLiters: number;
    incentivePerItem?: number;
    totalIncentive?: number;
};

export type MonthlyIncentiveChartItem = {
    month: string;
    totalIncentive: number;
};

export type GratisReasonChartItem = {
    reason: string;
    totalLiters: number;
};

export type ReportSalesRepresentative = {
    id: number;
    firstName: string;
    lastName: string;
};

export type MonthlyReportData = {
    salesRep: ReportSalesRepresentative | null;
    summary: ReportSummary[];
    products: ProductReportRow[];
};

export type YtdReportData = MonthlyReportData & {
    monthlyIncentives: MonthlyIncentiveChartItem[];
};

export type GratisReportData = {
    summary: ReportSummary[];
    products: ProductReportRow[];
    reasons: GratisReasonChartItem[];
};

export type MonthlyGratisReportData = GratisReportData;

export type GratisYtdReportData = GratisReportData;
