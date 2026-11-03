export type ReportType = "monthly" | "ytd" | "gratis-monthly" | "gratis-ytd";

export type ReportRequest = {
    type: ReportType;
    year: number;
    month: number | null;
    salesRepId: number | null;
    managerId: number | null;
};

export type ReportViewProps = {
    salesRepId: number | null;
    managerId: number | null;
    selectedDate: Date;
    onPeriodChange: (date: Date) => void;
    onLoaded: (data: { salesRep?: ReportSalesRepresentative | null }) => void;
};

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

export type SalesMetrics = { totalBottles: number; totalJugs: number; totalLiters: number; totalIncentive: number; salesRepIncentive?: number; managerIncentive?: number };

export type MonthlyReportData = {
    metrics: SalesMetrics;
    salesRep: ReportSalesRepresentative | null;
    summary: ReportSummary[];
    products: ProductReportRow[];
};

export type YtdReportData = MonthlyReportData & {
    monthlyIncentives: MonthlyIncentiveChartItem[];
};

export type GratisReportData = {
    salesRep?: ReportSalesRepresentative | null;
    summary: ReportSummary[];
    products: ProductReportRow[];
    reasons: GratisReasonChartItem[];
};

export type MonthlyGratisReportData = GratisReportData;

export type GratisYtdReportData = GratisReportData;
