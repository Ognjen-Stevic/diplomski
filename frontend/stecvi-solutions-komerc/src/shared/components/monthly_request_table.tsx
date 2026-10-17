import type { ReactNode } from "react";
import type { MonthlyRequestItemStatus, MonthlyRequestRecord } from "../types/monthly_request";

const STATUS_LABELS: Record<MonthlyRequestItemStatus, string> = {
    PENDING: "Na čekanju",
    APPROVED: "Odobreno",
    REJECTED: "Odbijeno",
};

const STATUS_CLASSES: Record<MonthlyRequestItemStatus, string> = {
    PENDING: "bg-amber-50 text-amber-800 ring-amber-200",
    APPROVED: "bg-emerald-50 text-emerald-800 ring-emerald-200",
    REJECTED: "bg-red-50 text-red-700 ring-red-200",
};

const formatDate = (date: string) => new Intl.DateTimeFormat("sr-Latn-RS", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
}).format(new Date(date));

export function MonthlyRequestTable({ records, dateColumnLabel, showStatus = false, showCustomerColumn = false, showSalesRepColumn = false, actionColumnLabel = "Akcije", renderActions, emptyMessage }: {
    records: MonthlyRequestRecord[];
    dateColumnLabel: string;
    showStatus?: boolean;
    showCustomerColumn?: boolean;
    showSalesRepColumn?: boolean;
    actionColumnLabel?: string;
    renderActions?: (record: MonthlyRequestRecord) => ReactNode;
    emptyMessage: string;
}) {
    const columnCount = 4 + Number(showStatus) + Number(showCustomerColumn) + Number(showSalesRepColumn) + Number(Boolean(renderActions));

    return (
        <section className="mt-6 overflow-hidden rounded-2xl border border-dust-grey bg-white shadow-sm">
            <div className="overflow-x-auto">
                <table className={`w-full border-collapse text-left ${showCustomerColumn || showSalesRepColumn ? "min-w-[920px]" : "min-w-[760px]"}`}>
                    <thead><tr className="bg-yale-blue/[0.04] text-xs uppercase tracking-wider text-graphite/55">
                        {showSalesRepColumn && <th className="px-5 py-3 font-bold sm:px-6">KOMERCIJALISTA</th>}
                        {showCustomerColumn && <th className="px-5 py-3 font-bold sm:px-6">KUPAC</th>}
                        <th className="px-5 py-3 font-bold sm:px-6">Proizvod</th>
                        <th className="px-5 py-3 text-right font-bold">Količina</th>
                        <th className="px-5 py-3 font-bold">Napomena</th>
                        <th className="px-5 py-3 font-bold">{dateColumnLabel}</th>
                        {showStatus && <th className="px-5 py-3 text-right font-bold sm:px-6">Status</th>}
                        {renderActions && <th className="px-5 py-3 text-right font-bold sm:px-6">{actionColumnLabel}</th>}
                    </tr></thead>
                    <tbody className="divide-y divide-dust-grey/75">
                        {records.length === 0 && <tr><td colSpan={columnCount} className="px-5 py-10 text-center text-sm text-graphite/55">{emptyMessage}</td></tr>}
                        {records.map((record) => (
                            <tr key={record.id} className="align-top transition hover:bg-yale-blue/[0.025]">
                                {showSalesRepColumn && (
                                    <th scope="row" className="whitespace-nowrap px-5 py-4 font-semibold text-graphite sm:px-6">
                                        {record.salesRep
                                            ? `${record.salesRep.firstName} ${record.salesRep.lastName}`
                                            : "—"}
                                    </th>
                                )}
                                {showCustomerColumn && (
                                    <th scope="row" className="px-5 py-4 sm:px-6">
                                        <span className="block font-semibold text-graphite">{record.customer?.objectName ?? "—"}</span>
                                        {record.customer?.companyName && (
                                            <span className="mt-0.5 block text-sm font-normal text-graphite/55">{record.customer.companyName}</span>
                                        )}
                                    </th>
                                )}
                                <td className="px-5 py-4 sm:px-6"><ul className="space-y-2">{record.items.map((item, index) => <li key={`${item.name}-${index}`} className="font-semibold text-graphite">{item.name}</li>)}</ul></td>
                                <td className="px-5 py-4 text-right"><ul className="space-y-2">{record.items.map((item, index) => <li key={`${item.name}-${index}`} className="font-bold tabular-nums text-yale-blue">{item.quantity}</li>)}</ul></td>
                                <td className="max-w-sm px-5 py-4 text-sm text-graphite/65">{record.note || "—"}</td>
                                <td className="whitespace-nowrap px-5 py-4 text-sm text-graphite/65">{formatDate(record.date)}</td>
                                {showStatus && (
                                    <td className="px-5 py-4 text-right sm:px-6">
                                        {record.status && (
                                            <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${STATUS_CLASSES[record.status]}`}>
                                                {STATUS_LABELS[record.status]}
                                            </span>
                                        )}
                                    </td>
                                )}
                                {renderActions && (
                                    <td className="whitespace-nowrap px-5 py-4 text-right sm:px-6">
                                        {renderActions(record)}
                                    </td>
                                )}
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </section>
    );
}
