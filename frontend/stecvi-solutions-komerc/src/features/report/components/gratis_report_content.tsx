import type { GratisReportData } from "../types/sales_report.types";
import { formatLiters } from "../utils/sales_report.utils";
import { ReportChart } from "./report_chart";
import { ReportSummaryCards } from "./report_summary_cards";
import { ReportTable } from "./report_table";

type GratisReportContentProps = {
    data: GratisReportData;
    periodLabel: string;
    description: string;
};

export function GratisReportContent({ data, periodLabel, description }: GratisReportContentProps) {
    return (
        <>
            <ReportSummaryCards summaries={data.summary} />
            <ReportChart
                title={`Gratis po razlogu — ${periodLabel}`}
                description="Ukupan broj litara grupisan prema razlogu gratis trebovanja."
                items={data.reasons.map((item) => ({ label: item.reason, value: item.totalLiters }))}
                formatValue={formatLiters}
                orientation="horizontal"
            />
            <ReportTable
                rows={data.products}
                title={`Gratis proizvodi — ${periodLabel}`}
                description={description}
                unitsLabel="Poklonjeno boca/komada"
            />
        </>
    );
}
