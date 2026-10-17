import { useEffect, useState } from "react";

import { PeriodSelector } from "../../../shared/components/period_selector";
import { reportApi } from "../report_api";
import type { YtdReportData } from "../types/sales_report.types";
import { formatRsd } from "../utils/sales_report.utils";
import { ReportChart } from "./report_chart";
import { ReportFeedback } from "./report_feedback";
import { ReportExportButton } from "./report_export_button";
import { ReportSummaryCards } from "./report_summary_cards";
import { ReportTable } from "./report_table";

type YtdReportProps = {
    salesRepId: number | null;
    managerId?: number | null;
};

export function YtdReport({ salesRepId, managerId = null }: YtdReportProps) {
    const [selectedDate, setSelectedDate] = useState(() => new Date(new Date().getFullYear(), 0, 1));
    const [data, setData] = useState<YtdReportData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [retryKey, setRetryKey] = useState(0);
    const [exporting, setExporting] = useState(false);
    const [exportError, setExportError] = useState("");

    useEffect(() => {
        const controller = new AbortController();
        reportApi.getYtdReport(selectedDate.getFullYear(), salesRepId, managerId, controller.signal)
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

    const changeYear = (value: Date) => {
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
            await reportApi.downloadYtdReport(selectedDate.getFullYear(), salesRepId, managerId);
        } catch (reason: unknown) {
            setExportError(reason instanceof Error ? reason.message : "Excel izveštaj nije preuzet.");
        } finally {
            setExporting(false);
        }
    };

    const selectedYear = selectedDate.getFullYear();

    return (
        <div>
            <PeriodSelector value={selectedDate} onChange={changeYear} mode="year" />
            <div className="mt-4 flex justify-end">
                <ReportExportButton exporting={exporting} disabled={loading || Boolean(error)} onClick={exportExcel} />
            </div>
            {exportError && <p role="alert" className="mt-3 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{exportError}</p>}
            <ReportFeedback loading={loading} error={error} onRetry={retry} />
            {!loading && !error && data && (
                <>
                    <ReportSummaryCards summaries={data.summary} />
                    <ReportChart
                        title={`Mesečna stimulacija — ${selectedYear}`}
                        description="Kretanje ukupno ostvarene stimulacije po mesecima izabrane godine."
                        items={data.monthlyIncentives.map((item) => ({ label: item.month, value: item.totalIncentive }))}
                        formatValue={formatRsd}
                    />
                    <ReportTable
                        rows={data.products}
                        title={`Prodaja po proizvodu — YTD ${selectedYear}`}
                        description="Zbirni rezultati za izabranu godinu."
                        unitsLabel="Prodato boca/komada"
                        showIncentive
                    />
                </>
            )}
        </div>
    );
}
