import { apiRequest, downloadAuthenticatedFile } from "../../app/api/http_client";
import type {
    GratisYtdReportData,
    MonthlyGratisReportData,
    MonthlyReportData,
    ProductReportRow,
    ReportSalesRepresentative,
    ReportSummary,
    YtdReportData,
} from "./types/sales_report.types";

type ApiSalesSummary = {
    totalBottles: number;
    totalJugs: number;
    totalLiters: number;
    totalIncentive: number;
    salesRepIncentive?: number | null;
    managerIncentive?: number | null;
};

type ApiGratisSummary = {
    totalItems: number;
    totalBottles: number;
    totalJugs: number;
    totalLiters: number;
};

type ApiProductReportRow = ProductReportRow;

type ApiMonthlyReport = {
    year: number;
    month: number;
    salesRep: ReportSalesRepresentative | null;
    summary: ApiSalesSummary;
    products: ApiProductReportRow[];
};

type ApiYtdReport = {
    year: number;
    salesRep: ReportSalesRepresentative | null;
    summary: ApiSalesSummary;
    products: ApiProductReportRow[];
    monthlyIncentives: Array<{ month: number; totalIncentive: number }>;
};

type ApiGratisReason = "LISTING" | "CONSUMPTION" | "GIFT" | "OTHER";

type ApiGratisYtdReport = {
    year: number;
    salesRep?: ReportSalesRepresentative | null;
    summary: ApiGratisSummary;
    products: ApiProductReportRow[];
    reasons: Array<{ reason: ApiGratisReason; totalLiters: number }>;
};

type ApiMonthlyGratisReport = ApiGratisYtdReport & {
    month: number;
};

const GRATIS_REASON_LABELS: Record<ApiGratisReason, string> = {
    LISTING: "Listing stimulacija",
    CONSUMPTION: "Stimulacija potrošnje",
    GIFT: "Poklon",
    OTHER: "Ostalo",
};

async function downloadReport(path: string, fallbackFilename: string, signal?: AbortSignal): Promise<void> {
    return downloadAuthenticatedFile(
        path,
        fallbackFilename,
        "Excel izveštaj nije preuzet.",
        signal,
    );
}

function toProducts(products: ApiProductReportRow[]): ProductReportRow[] {
    return products.map((product) => ({
        ...product,
        units: Number(product.units),
        volumeLiters: Number(product.volumeLiters),
        incentivePerItem: product.incentivePerItem == null ? undefined : Number(product.incentivePerItem),
        totalIncentive: product.totalIncentive == null ? undefined : Number(product.totalIncentive),
    }));
}

function salesRepQuery(salesRepId: number | null): string {
    return salesRepId == null ? "" : `&salesRepId=${encodeURIComponent(salesRepId)}`;
}

function managerQuery(managerId: number | null): string {
    return managerId == null ? "" : `&managerId=${encodeURIComponent(managerId)}`;
}

function salesSummaries(summary: ApiSalesSummary, period: "monthly" | "ytd"): ReportSummary[] {
    const suffix = period === "ytd" ? " YTD" : "";
    const summaries: ReportSummary[] = [
        {
            id: "bottles",
            label: `Prodate boce${suffix}`,
            value: Number(summary.totalBottles),
            format: "integer",
            tone: "blue",
            description: "Ukupan broj prodatih boca",
        },
        {
            id: "jugs",
            label: `Prodati baloni${suffix}`,
            value: Number(summary.totalJugs),
            format: "integer",
            tone: "teal",
            description: "Ukupan broj prodatih balona",
        },
        {
            id: "liters",
            label: `Prodato litara${suffix}`,
            value: Number(summary.totalLiters),
            format: "liters",
            tone: "teal",
            description: period === "ytd" ? "Ukupna zapremina u godini" : "Ukupna zapremina proizvoda",
        },
    ];

    if (summary.salesRepIncentive != null && summary.managerIncentive != null) {
        summaries.push(
            { id: "sales-rep-incentive", label: "Stimulacija komercijalista", value: Number(summary.salesRepIncentive), format: "currency", tone: "amber" },
            { id: "manager-incentive", label: "Stimulacija menadžera", value: Number(summary.managerIncentive), format: "currency", tone: "amber" },
            { id: "company-incentive", label: "Ukupna stimulacija kompanije", value: Number(summary.totalIncentive), format: "currency", tone: "amber" },
        );
    } else if (period === "monthly") {
        summaries.push({
            id: "incentive",
            label: "Mesečna stimulacija",
            value: Number(summary.totalIncentive),
            format: "currency",
            tone: "amber",
            description: "Ukupan obračun za period",
        });
    }

    return summaries;
}

function toGratisReport(result: ApiGratisYtdReport): GratisYtdReportData {
    return {
        salesRep: result.salesRep,
        summary: [
            {
                id: "bottles",
                label: "Poklonjeno boca",
                value: Number(result.summary.totalBottles),
                format: "integer",
                tone: "blue",
                description: "Ukupan broj poklonjenih boca",
            },
            {
                id: "jugs",
                label: "Poklonjeno balona",
                value: Number(result.summary.totalJugs),
                format: "integer",
                tone: "teal",
                description: "Ukupan broj poklonjenih balona",
            },
            {
                id: "liters",
                label: "Poklonjeno litara",
                value: Number(result.summary.totalLiters),
                format: "liters",
                tone: "teal",
                description: "Ukupna gratis zapremina",
            },
        ],
        products: toProducts(result.products),
        reasons: result.reasons.map((item) => ({
            reason: GRATIS_REASON_LABELS[item.reason],
            totalLiters: Number(item.totalLiters),
        })),
    };
}

export const reportApi = {
    async getMonthlyReport(
        year: number,
        month: number,
        salesRepId: number | null = null,
        managerId: number | null = null,
        signal?: AbortSignal,
    ): Promise<MonthlyReportData> {
        const result = await apiRequest<ApiMonthlyReport>(
            `/api/sales-reports/monthly?year=${year}&month=${month}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            { signal },
        );
        return {
            metrics: { totalBottles: Number(result.summary.totalBottles), totalJugs: Number(result.summary.totalJugs), totalLiters: Number(result.summary.totalLiters), totalIncentive: Number(result.summary.totalIncentive), salesRepIncentive: result.summary.salesRepIncentive == null ? undefined : Number(result.summary.salesRepIncentive), managerIncentive: result.summary.managerIncentive == null ? undefined : Number(result.summary.managerIncentive) },
            salesRep: result.salesRep,
            summary: salesSummaries(result.summary, "monthly"),
            products: toProducts(result.products),
        };
    },

    async getYtdReport(
        year: number,
        salesRepId: number | null = null,
        managerId: number | null = null,
        signal?: AbortSignal,
    ): Promise<YtdReportData> {
        const result = await apiRequest<ApiYtdReport>(
            `/api/sales-reports/ytd?year=${year}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            { signal },
        );
        return {
            metrics: { totalBottles: Number(result.summary.totalBottles), totalJugs: Number(result.summary.totalJugs), totalLiters: Number(result.summary.totalLiters), totalIncentive: Number(result.summary.totalIncentive), salesRepIncentive: result.summary.salesRepIncentive == null ? undefined : Number(result.summary.salesRepIncentive), managerIncentive: result.summary.managerIncentive == null ? undefined : Number(result.summary.managerIncentive) },
            salesRep: result.salesRep,
            summary: salesSummaries(result.summary, "ytd"),
            products: toProducts(result.products),
            monthlyIncentives: result.monthlyIncentives.map((item) => ({
                month: new Intl.DateTimeFormat("sr-Latn-RS", { month: "short" })
                    .format(new Date(result.year, item.month - 1, 1)),
                totalIncentive: Number(item.totalIncentive),
            })),
        };
    },

    downloadMonthlyReport(year: number, month: number, salesRepId: number | null = null, managerId: number | null = null, signal?: AbortSignal): Promise<void> {
        const filename = `monthly-report-${year}-${String(month).padStart(2, "0")}.xlsx`;
        return downloadReport(
            `/api/sales-reports/monthly/export?year=${year}&month=${month}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            filename,
            signal,
        );
    },

    downloadYtdReport(year: number, salesRepId: number | null = null, managerId: number | null = null, signal?: AbortSignal): Promise<void> {
        return downloadReport(
            `/api/sales-reports/ytd/export?year=${year}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            `ytd-report-${year}.xlsx`,
            signal,
        );
    },

    downloadGratisYtdReport(year: number, salesRepId: number | null = null, managerId: number | null = null, signal?: AbortSignal): Promise<void> {
        return downloadReport(
            `/api/sales-reports/gratis-ytd/export?year=${year}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            `gratis-ytd-report-${year}.xlsx`,
            signal,
        );
    },

    downloadMonthlyGratisReport(year: number, month: number, salesRepId: number | null = null, managerId: number | null = null, signal?: AbortSignal): Promise<void> {
        const paddedMonth = String(month).padStart(2, "0");
        return downloadReport(
            `/api/sales-reports/gratis-monthly/export?year=${year}&month=${month}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            `monthly-gratis-report-${year}-${paddedMonth}.xlsx`,
            signal,
        );
    },

    async getMonthlyGratisReport(
        year: number,
        month: number,
        salesRepId: number | null = null,
        managerId: number | null = null,
        signal?: AbortSignal,
    ): Promise<MonthlyGratisReportData> {
        const result = await apiRequest<ApiMonthlyGratisReport>(
            `/api/sales-reports/gratis-monthly?year=${year}&month=${month}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            { signal },
        );
        return toGratisReport(result);
    },

    async getGratisYtdReport(year: number, salesRepId: number | null = null, managerId: number | null = null, signal?: AbortSignal): Promise<GratisYtdReportData> {
        const result = await apiRequest<ApiGratisYtdReport>(`/api/sales-reports/gratis-ytd?year=${year}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`, { signal });
        return toGratisReport(result);
    },
};
