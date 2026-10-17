import type { ReportSummary } from "../types/sales_report.types";
import { ReportCard } from "./report_card";

export function ReportSummaryCards({ summaries }: { summaries: ReportSummary[] }) {
    const desktopColumns = summaries.length === 4 ? "xl:grid-cols-4" : "xl:grid-cols-3";

    return (
        <section aria-label="Sažetak izveštaja" className={`mt-6 grid gap-4 md:grid-cols-2 ${desktopColumns}`}>
            {summaries.map((summary) => <ReportCard key={summary.id} summary={summary} />)}
        </section>
    );
}
