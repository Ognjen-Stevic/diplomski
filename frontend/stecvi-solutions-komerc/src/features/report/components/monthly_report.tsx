import { useEffect, useState } from "react";

import { PeriodSelector } from "../../../shared/components/period_selector";
import { reportApi } from "../report_api";
import type { MonthlyReportData } from "../types/sales_report.types";
import { ReportFeedback } from "./report_feedback";
import { ReportExportButton } from "./report_export_button";
import { ReportSummaryCards } from "./report_summary_cards";
import { ReportTable } from "./report_table";

type MonthlyReportProps = {
    salesRepId: number | null;
    managerId?: number | null;
};

export function MonthlyReport({ salesRepId, managerId = null }: MonthlyReportProps) {
    const [selectedDate, setSelectedDate] = useState(() => {
        const current = new Date();
        return new Date(current.getFullYear(), current.getMonth(), 1);
    });
    const [data, setData] = useState<MonthlyReportData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [retryKey, setRetryKey] = useState(0);
    const [exporting, setExporting] = useState(false);
    const [exportError, setExportError] = useState("");
    const period = new Intl.DateTimeFormat("sr-Latn-RS", { month: "long", year: "numeric" }).format(selectedDate);

    useEffect(() => {
        const controller = new AbortController();
        reportApi.getMonthlyReport(
            selectedDate.getFullYear(),
            selectedDate.getMonth() + 1,
            salesRepId,
            managerId,
            controller.signal,
        )
            .then(setData)
            .catch((reason: unknown) => {
                if (!controller.signal.aborted) {
                    setError(reason instanceof Error ? reason.message : "Izveštaj nije učitan.");
                }
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [managerId, selectedDate, retryKey, salesRepId]);

    const changePeriod = (value: Date) => {
        setLoading(true);
        setError("");
        setExportError("");
        setSelectedDate(value);
    };

    const retry = () => {
        setLoading(true);
        setError("");
        setExportError("");
        setRetryKey((key) => key + 1);
    };

    const exportExcel = async () => {
        if (exporting) return;
        setExporting(true);
        setExportError("");
        try {
            await reportApi.downloadMonthlyReport(
                selectedDate.getFullYear(),
                selectedDate.getMonth() + 1,
                salesRepId,
                managerId,
            );
        } catch (reason: unknown) {
            setExportError(reason instanceof Error ? reason.message : "Excel izveštaj nije preuzet.");
        } finally {
            setExporting(false);
        }
    };

    return (
        <div>
            <PeriodSelector value={selectedDate} onChange={changePeriod} />
            <div className="mt-4 flex justify-end">
                <ReportExportButton exporting={exporting} disabled={loading || Boolean(error)} onClick={exportExcel} />
            </div>
            {exportError && <p role="alert" className="mt-3 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{exportError}</p>}
            <ReportFeedback loading={loading} error={error} onRetry={retry} />
            {!loading && !error && data && (
                <>
                    <ReportSummaryCards summaries={data.summary} />
                    <ReportTable
                        rows={data.products}
                        title={`Prodaja po proizvodu — ${period}`}
                        description="Količine, zapremina i stimulacija po prodatom proizvodu."
                        unitsLabel="Prodato boca/komada"
                        showIncentive
                    />
                </>
            )}
        </div>
    );
}
