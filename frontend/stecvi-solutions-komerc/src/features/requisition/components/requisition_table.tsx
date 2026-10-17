import type { RequisitionListItem, RequisitionStatus } from "../types/requisition.types";
import { REQUISITION_STATUS_LABELS } from "../data/requisition.data";

const STATUS_CLASSES: Record<RequisitionStatus, string> = {
    draft: "bg-slate-100 text-slate-700 ring-slate-200",
    submitted: "bg-amber-50 text-amber-800 ring-amber-200",
    approved: "bg-blue-50 text-blue-800 ring-blue-200",
    rejected: "bg-red-50 text-red-700 ring-red-200",
    rejected_by_bookkeeper: "bg-red-50 text-red-700 ring-red-200",
    processing: "bg-violet-50 text-violet-800 ring-violet-200",
    completed: "bg-emerald-50 text-emerald-800 ring-emerald-200",
    cancelled_by_manager: "bg-red-50 text-red-700 ring-red-200",
    cancelled_by_bookkeeper: "bg-red-50 text-red-700 ring-red-200",
};

const formatDate = (date: string) => new Intl.DateTimeFormat("sr-Latn-RS", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
}).format(new Date(date));

export function RequisitionTable({ requisitions, period, showSalesRep = false, onSelect }: {
    requisitions: RequisitionListItem[];
    period: string;
    showSalesRep?: boolean;
    onSelect: (requisition: RequisitionListItem) => void;
}) {
    return (
        <section className="mt-6 overflow-hidden rounded-2xl border border-dust-grey bg-white shadow-sm">
            <div className="border-b border-dust-grey px-5 py-4 sm:px-6">
                <h2 className="text-lg font-bold capitalize text-yale-blue">Trebovanja — {period}</h2>
                <p className="mt-0.5 text-sm text-graphite/55">{requisitions.length} zapisa</p>
            </div>
            <div className="overflow-x-auto">
                <table className="w-full min-w-[640px] border-collapse text-left">
                    <thead><tr className="bg-yale-blue/[0.04] text-xs uppercase tracking-wider text-graphite/55">
                        {showSalesRep && <th scope="col" className="px-5 py-3 font-bold sm:px-6">Komercijalista</th>}
                        <th scope="col" className="px-5 py-3 font-bold sm:px-6">Kupac</th><th scope="col" className="px-5 py-3 font-bold">Vrsta</th><th scope="col" className="px-5 py-3 font-bold">Datum</th><th scope="col" className="px-5 py-3 text-right font-bold sm:px-6">Status</th>
                    </tr></thead>
                    <tbody className="divide-y divide-dust-grey/75">
                        {requisitions.length === 0 && <tr><td colSpan={showSalesRep ? 5 : 4} className="px-5 py-10 text-center text-sm text-graphite/55">Nema trebovanja za izabrani period.</td></tr>}
                        {requisitions.map((requisition) => (
                            <tr key={requisition.id} role="button" tabIndex={0} onClick={() => onSelect(requisition)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); onSelect(requisition); } }} className="cursor-pointer transition hover:bg-yale-blue/[0.045] focus:bg-yale-blue/[0.045] focus:outline-none">
                                {showSalesRep && (
                                    <td className="whitespace-nowrap px-5 py-4 font-semibold text-graphite sm:px-6">
                                        {requisition.salesRep.firstName} {requisition.salesRep.lastName}
                                    </td>
                                )}
                                <th scope="row" className="px-5 py-4 sm:px-6"><span className="block font-semibold text-graphite">{requisition.buyerName}</span><span className="mt-0.5 block text-sm font-normal text-graphite/55">{requisition.companyName}</span></th>
                                <td className="px-5 py-4"><span className={`inline-flex rounded-lg px-2.5 py-1 text-xs font-bold ${requisition.type === "gratis" ? "bg-stormy-teal/10 text-stormy-teal" : "bg-yale-blue/10 text-yale-blue"}`}>{requisition.type === "gratis" ? "Gratis" : "Klasično"}</span></td>
                                <td className="whitespace-nowrap px-5 py-4 text-sm text-graphite/65">{formatDate(requisition.createdAt)}</td>
                                <td className="px-5 py-4 text-right sm:px-6"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${STATUS_CLASSES[requisition.status]}`}>{REQUISITION_STATUS_LABELS[requisition.status]}</span></td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </section>
    );
}
