import { useCallback } from "react";

import { PeriodSelector } from "../../../shared/components/period_selector";
import { useReportData } from "../hooks/use_report_data";
import { useReportExport } from "../hooks/use_report_export";
import { reportApi } from "../report_api";
import type { ReportViewProps } from "../types/sales_report.types";
import { GratisReportContent } from "./gratis_report_content";
import { ReportFeedback } from "./report_feedback";
import { ReportExportButton } from "./report_export_button";

export function GratisYtdReport({ salesRepId, managerId, selectedDate, onPeriodChange, onLoaded }: ReportViewProps) {
    const year = selectedDate.getFullYear();
    const load = useCallback((signal: AbortSignal) =>
        reportApi.getGratisYtdReport(year, salesRepId, managerId, signal), [year, salesRepId, managerId]);
    const { data, loading, error, retry, identity } = useReportData(
        { type: "gratis-ytd", year, month: null, salesRepId, managerId }, load, onLoaded,
    );
    const download = useCallback((signal: AbortSignal) =>
        reportApi.downloadGratisYtdReport(year, salesRepId, managerId, signal), [year, salesRepId, managerId]);
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
                <GratisReportContent
                    data={data}
                    periodLabel={`YTD ${selectedYear}`}
                    description="Poklonjene količine za izabranu godinu."
                />
            )}
        </div>
    );
}
