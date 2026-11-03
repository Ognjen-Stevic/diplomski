import type { ReportType } from "../types/sales_report.types";

const REPORT_TYPES: ReportType[] = ["monthly", "ytd", "gratis-monthly", "gratis-ytd"];

export function positiveId(value: string | null): number | null {
    if (!value || !/^\d+$/.test(value)) return null;
    const number = Number(value);
    return Number.isSafeInteger(number) && number > 0 ? number : null;
}

export function readReportNavigation(search: URLSearchParams, now = new Date()) {
    const requestedType = search.get("type");
    const type = REPORT_TYPES.find((type) => type === requestedType) ?? "monthly";
    const requestedYear = positiveId(search.get("year"));
    const requestedMonth = positiveId(search.get("month"));
    const year = requestedYear !== null && requestedYear >= 2000 && requestedYear <= 2100
        ? requestedYear : now.getFullYear();
    const month = requestedMonth !== null && requestedMonth <= 12 ? requestedMonth : now.getMonth() + 1;
    return { type, year, month,
        salesRepId: positiveId(search.get("salesRepId")),
        managerId: positiveId(search.get("managerId")),
    };
}

export function updateReportNavigation(search: URLSearchParams, type: ReportType, date: Date) {
    const next = new URLSearchParams(search);
    next.set("type", type);
    next.set("year", String(date.getFullYear()));
    // Retain the monthly selection across YTD tabs; yearly APIs never receive it.
    next.set("month", String(date.getMonth() + 1));
    return next;
}
