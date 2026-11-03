import { useCallback } from "react";

import { PeriodSelector } from "../../../shared/components/period_selector";
import { useReportData } from "../hooks/use_report_data";
import { useReportExport } from "../hooks/use_report_export";
import { reportApi } from "../report_api";
import type { ReportViewProps } from "../types/sales_report.types";
import { formatRsd } from "../utils/sales_report.utils";
import { ReportChart } from "./report_chart";
import { ReportFeedback } from "./report_feedback";
import { ReportExportButton } from "./report_export_button";
import { ReportSummaryCards } from "./report_summary_cards";
import { ReportTable } from "./report_table";

export function YtdReport({ salesRepId, managerId, selectedDate, onPeriodChange, onLoaded }: ReportViewProps) {
    const year = selectedDate.getFullYear();
    const load = useCallback((signal: AbortSignal) =>
        reportApi.getYtdReport(year, salesRepId, managerId, signal), [year, salesRepId, managerId]);
    const { data, loading, error, retry, identity } = useReportData(
        { type: "ytd", year, month: null, salesRepId, managerId }, load, onLoaded,
    );
    const download = useCallback((signal: AbortSignal) =>
        reportApi.downloadYtdReport(year, salesRepId, managerId, signal), [year, salesRepId, managerId]);
    const { exporting, exportError, exportExcel } = useReportExport(identity, download);
    const selectedYear = year;

    return (
        <div>
            <PeriodSelector value={selectedDate} onChange={onPeriodChange} mode="year" />
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
