import { authenticatedFetch } from "../../app/api/http_client";
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

async function request<T>(path: string, signal?: AbortSignal): Promise<T> {
    const response = await authenticatedFetch(path, {
        signal,
    });

    if (!response.ok) {
        let message = "Izveštaj nije učitan.";
        try {
            const body = await response.json() as { message?: string; detail?: string };
            message = body.message ?? body.detail ?? message;
        } catch {
            // Use the fallback message when the server does not return JSON.
        }
        throw new Error(message);
    }
    return response.json() as Promise<T>;
}

async function downloadReport(path: string, fallbackFilename: string): Promise<void> {
    const response = await authenticatedFetch(path);

    if (!response.ok) {
        let message = "Excel izveštaj nije preuzet.";
        try {
            const body = await response.json() as { message?: string; detail?: string };
            message = body.message ?? body.detail ?? message;
        } catch {
            // Use the fallback message when the server does not return JSON.
        }
        throw new Error(message);
    }

    const filename = getDownloadFilename(response.headers.get("Content-Disposition")) ?? fallbackFilename;
    const objectUrl = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
}

function getDownloadFilename(contentDisposition: string | null): string | null {
    if (!contentDisposition) {
        return null;
    }

    const encodedFilename = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
    if (encodedFilename) {
        try {
            return decodeURIComponent(encodedFilename);
        } catch {
            return encodedFilename;
        }
    }

    return contentDisposition.match(/filename="?([^";]+)"?/i)?.[1] ?? null;
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

    if (period === "monthly") {
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
        const result = await request<ApiMonthlyReport>(
            `/api/sales-reports/monthly?year=${year}&month=${month}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            signal,
        );
        return {
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
        const result = await request<ApiYtdReport>(
            `/api/sales-reports/ytd?year=${year}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            signal,
        );
        return {
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

    downloadMonthlyReport(year: number, month: number, salesRepId: number | null = null, managerId: number | null = null): Promise<void> {
        const filename = `monthly-report-${year}-${String(month).padStart(2, "0")}.xlsx`;
        return downloadReport(
            `/api/sales-reports/monthly/export?year=${year}&month=${month}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            filename,
        );
    },

    downloadYtdReport(year: number, salesRepId: number | null = null, managerId: number | null = null): Promise<void> {
        return downloadReport(
            `/api/sales-reports/ytd/export?year=${year}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            `ytd-report-${year}.xlsx`,
        );
    },

    downloadGratisYtdReport(year: number, salesRepId: number | null = null, managerId: number | null = null): Promise<void> {
        return downloadReport(
            `/api/sales-reports/gratis-ytd/export?year=${year}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            `gratis-ytd-report-${year}.xlsx`,
        );
    },

    downloadMonthlyGratisReport(year: number, month: number, salesRepId: number | null = null, managerId: number | null = null): Promise<void> {
        const paddedMonth = String(month).padStart(2, "0");
        return downloadReport(
            `/api/sales-reports/gratis-monthly/export?year=${year}&month=${month}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            `monthly-gratis-report-${year}-${paddedMonth}.xlsx`,
        );
    },

    async getMonthlyGratisReport(
        year: number,
        month: number,
        salesRepId: number | null = null,
        managerId: number | null = null,
        signal?: AbortSignal,
    ): Promise<MonthlyGratisReportData> {
        const result = await request<ApiMonthlyGratisReport>(
            `/api/sales-reports/gratis-monthly?year=${year}&month=${month}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`,
            signal,
        );
        return toGratisReport(result);
    },

    async getGratisYtdReport(year: number, salesRepId: number | null = null, managerId: number | null = null, signal?: AbortSignal): Promise<GratisYtdReportData> {
        const result = await request<ApiGratisYtdReport>(`/api/sales-reports/gratis-ytd?year=${year}${salesRepQuery(salesRepId)}${managerQuery(managerId)}`, signal);
        return toGratisReport(result);
    },
};
