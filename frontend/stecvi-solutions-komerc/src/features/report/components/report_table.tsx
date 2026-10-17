import { resolveProductPictureUrl } from "../../product/product_api";
import type { ProductReportRow } from "../types/sales_report.types";
import { formatInteger, formatLiters, formatRsd } from "../utils/sales_report.utils";

type ReportTableProps = {
    rows: ProductReportRow[];
    title: string;
    description: string;
    unitsLabel: string;
    showIncentive?: boolean;
};

export function ReportTable({ rows, title, description, unitsLabel, showIncentive = false }: ReportTableProps) {
    return (
        <section className="mt-6 overflow-hidden rounded-2xl border border-dust-grey bg-white shadow-sm">
            <div className="border-b border-dust-grey px-5 py-4 sm:px-6">
                <h2 className="text-lg font-bold text-yale-blue">{title}</h2>
                <p className="mt-0.5 text-sm text-graphite/55">{description}</p>
            </div>
            <div className="overflow-x-auto">
                <table className={`w-full border-collapse text-left ${showIncentive ? "min-w-[900px]" : "min-w-[620px]"}`}>
                    <thead>
                        <tr className="bg-yale-blue/[0.04] text-xs uppercase tracking-wider text-graphite/55">
                            <th scope="col" className="px-5 py-3 font-bold sm:px-6">Proizvod</th>
                            <th scope="col" className="px-5 py-3 text-right font-bold">{unitsLabel}</th>
                            <th scope="col" className="px-5 py-3 text-right font-bold">Litara po artiklu</th>
                            {showIncentive && <th scope="col" className="px-5 py-3 text-right font-bold">Stimulacija po artiklu</th>}
                            {showIncentive && <th scope="col" className="px-5 py-3 text-right font-bold sm:px-6">Ukupna stimulacija</th>}
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-dust-grey/75">
                        {rows.length === 0 && (
                            <tr><td colSpan={showIncentive ? 5 : 3} className="px-5 py-10 text-center text-sm text-graphite/55">Nema podataka za izabrani period.</td></tr>
                        )}
                        {rows.map((row) => (
                            <tr key={row.productCode} className="transition hover:bg-yale-blue/[0.025]">
                                <th scope="row" className="px-5 py-3 sm:px-6">
                                    <span className="flex items-center gap-3">
                                        <img src={resolveProductPictureUrl(row.pictureUrl)} alt="" className="h-12 w-12 shrink-0 rounded-xl border border-dust-grey object-cover" />
                                        <span className="min-w-0">
                                            <span className="block font-semibold text-graphite">{row.productName}</span>
                                            <span className="mt-0.5 block text-xs font-normal text-graphite/50">{row.productCode}</span>
                                        </span>
                                    </span>
                                </th>
                                <td className="px-5 py-3 text-right font-bold tabular-nums text-yale-blue">{formatInteger(row.units)}</td>
                                <td className="px-5 py-3 text-right tabular-nums text-graphite/70">{formatLiters(row.volumeLiters)}</td>
                                {showIncentive && <td className="px-5 py-3 text-right tabular-nums text-graphite/70">{formatRsd(row.incentivePerItem ?? 0)}</td>}
                                {showIncentive && <td className="px-5 py-3 text-right font-bold tabular-nums text-yale-blue sm:px-6">{formatRsd(row.totalIncentive ?? 0)}</td>}
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </section>
    );
}
