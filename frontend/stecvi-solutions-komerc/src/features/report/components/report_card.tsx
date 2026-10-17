import { BadgeDollarSign, Droplets, PackageCheck } from "lucide-react";

import type { ReportSummary } from "../types/sales_report.types";
import { formatInteger, formatLiters, formatRsd } from "../utils/sales_report.utils";

const TONE_STYLES: Record<ReportSummary["tone"], { icon: string; accent: string }> = {
    blue: { icon: "bg-yale-blue/10 text-yale-blue", accent: "border-l-yale-blue" },
    teal: { icon: "bg-stormy-teal/10 text-stormy-teal", accent: "border-l-stormy-teal" },
    amber: { icon: "bg-amber-50 text-amber-700", accent: "border-l-amber-500" },
};

const formatValue = (summary: ReportSummary) => {
    if (summary.format === "liters") return formatLiters(summary.value);
    if (summary.format === "currency") return formatRsd(summary.value);
    return formatInteger(summary.value);
};

export function ReportCard({ summary }: { summary: ReportSummary }) {
    const styles = TONE_STYLES[summary.tone];
    const Icon = summary.format === "liters"
        ? Droplets
        : summary.format === "currency"
            ? BadgeDollarSign
            : PackageCheck;

    return (
        <article className={`flex items-center gap-4 rounded-2xl border border-l-4 border-dust-grey bg-white p-5 shadow-sm ${styles.accent}`}>
            <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${styles.icon}`}>
                <Icon className="h-5 w-5" aria-hidden="true" />
            </span>
            <div className="min-w-0">
                <p className="text-2xl font-bold tabular-nums text-yale-blue">{formatValue(summary)}</p>
                <h3 className="mt-0.5 text-sm font-semibold text-graphite">{summary.label}</h3>
                {summary.description && <p className="mt-1 text-xs text-graphite/50">{summary.description}</p>}
            </div>
        </article>
    );
}
