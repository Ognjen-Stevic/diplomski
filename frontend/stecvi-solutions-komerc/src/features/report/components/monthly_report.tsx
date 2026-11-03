import { useCallback } from "react";

import { PeriodSelector } from "../../../shared/components/period_selector";
import { useReportData } from "../hooks/use_report_data";
import { useReportExport } from "../hooks/use_report_export";
import { reportApi } from "../report_api";
import type { ReportViewProps } from "../types/sales_report.types";
import { ReportFeedback } from "./report_feedback";
import { ReportExportButton } from "./report_export_button";
import { ReportSummaryCards } from "./report_summary_cards";
import { ReportTable } from "./report_table";

export function MonthlyReport({ salesRepId, managerId, selectedDate, onPeriodChange, onLoaded }: ReportViewProps) {
    const year = selectedDate.getFullYear();
    const month = selectedDate.getMonth() + 1;
    const load = useCallback((signal: AbortSignal) =>
        reportApi.getMonthlyReport(year, month, salesRepId, managerId, signal), [year, month, salesRepId, managerId]);
    const { data, loading, error, retry, identity } = useReportData(
        { type: "monthly", year, month, salesRepId, managerId }, load, onLoaded,
    );
    const download = useCallback((signal: AbortSignal) =>
        reportApi.downloadMonthlyReport(year, month, salesRepId, managerId, signal), [year, month, salesRepId, managerId]);
    const { exporting, exportError, exportExcel } = useReportExport(identity, download);
    const period = new Intl.DateTimeFormat("sr-Latn-RS", { month: "long", year: "numeric" }).format(selectedDate);

    return (
        <div>
            <PeriodSelector value={selectedDate} onChange={onPeriodChange} />
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
