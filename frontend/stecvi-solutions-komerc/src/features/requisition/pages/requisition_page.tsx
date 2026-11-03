import { useMonthlyRequisitions } from "../hooks/use_requisition_data";
import { useRequisitionNavigation } from "../hooks/use_requisition_navigation";
import { APP_ROUTES } from "../../../app/routes";
import { useMemo, useState } from "react";
import { Plus, Search, X } from "lucide-react";
import { Navigate, useNavigate } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { PERMISSIONS } from "../../../roles/permissions";
import { useAuth } from "../../auth/hooks/useAuth";
import { RequisitionDetailsPanel } from "../components/requisition_details_panel";
import { PeriodSelector } from "../../../shared/components/period_selector";
import { RequisitionSummaryCards } from "../components/requisition_summary_cards";
import { RequisitionTable } from "../components/requisition_table";
import type { RequisitionStatus } from "../types/requisition.types";
import { requisitionApi } from "../requisition_api";
import { REQUISITION_STATUS_LABELS } from "../data/requisition.data";

export default function RequisitionPage() {
    const navigate = useNavigate();
    const { user, hasPermission } = useAuth();
    const navigation = useRequisitionNavigation();
    const selectedDate = navigation.date;
    const query = useMonthlyRequisitions(navigation.year, navigation.month);
    const requisitions = useMemo(() => query.data?.requisitions ?? [], [query.data]);
    const { loading, error } = query;
    const [selection, setSelection] = useState<{ id: number; context: object } | null>(null);
    const selectedRequisition = selection?.context === query.context
        ? requisitions.find((item) => item.id === selection.id) ?? null : null;
    const totalCount = query.data?.totalCount ?? 0;
    const gratisCount = query.data?.gratisCount ?? 0;
    const [customerFilter, setCustomerFilter] = useState("");
    const statusFilter = navigation.status;
    const updateStatusFilter = (status: RequisitionStatus | "ALL") => navigation.update(selectedDate, status);
    const selectedPeriod = new Intl.DateTimeFormat("sr-Latn-RS", {
        month: "long",
        year: "numeric",
    }).format(selectedDate);
    const filteredRequisitions = useMemo(() => {
        if (user?.role !== "BOOKKEEPER") return requisitions;
        const customerTerm = customerFilter.trim().toLocaleLowerCase("sr-Latn-RS");
        return requisitions.filter((requisition) => {
            const matchesCustomer = !customerTerm
                || `${requisition.buyerName} ${requisition.companyName}`
                    .toLocaleLowerCase("sr-Latn-RS")
                    .includes(customerTerm);
            const matchesStatus = statusFilter === "ALL" || requisition.status === statusFilter;
            return matchesCustomer && matchesStatus;
        });
    }, [customerFilter, requisitions, statusFilter, user?.role]);
    const availableStatuses = useMemo(() => {
        const statuses = new Set(requisitions.map((requisition) => requisition.status));
        if (statusFilter !== "ALL") statuses.add(statusFilter);
        return Object.keys(REQUISITION_STATUS_LABELS)
            .filter((status): status is RequisitionStatus => statuses.has(status as RequisitionStatus));
    }, [requisitions, statusFilter]);
    const filtersActive = customerFilter.trim() !== "" || statusFilter !== "ALL";

    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (!hasPermission(PERMISSIONS.REQUISITIONS_READ)) return <Navigate to={APP_ROUTES.home} replace />;

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Evidencija trebovanja</p>
                        <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">Trebovanja</h1>
                        <p className="mt-2 text-sm text-graphite/60">Pregled svih kreiranih trebovanja.</p>
                    </div>
                    {hasPermission(PERMISSIONS.REQUISITIONS_CREATE) && (
                        <button type="button" onClick={() => navigate(APP_ROUTES.newRequisition)} className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white shadow-sm transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2">
                            <Plus className="h-5 w-5" />
                            Novo trebovanje
                        </button>
                    )}
                </header>

                <PeriodSelector value={selectedDate} onChange={(date) => navigation.update(date, navigation.status)} />
                {user.role === "BOOKKEEPER" && (
                    <section
                        aria-label="Filteri trebovanja"
                        className="mt-5 grid gap-3 rounded-2xl border border-dust-grey bg-white p-4 shadow-sm md:grid-cols-[minmax(0,1fr)_260px_auto]"
                    >
                        <label className="relative">
                            <span className="sr-only">Naziv kupca ili lokacije</span>
                            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-graphite/45" />
                            <input
                                type="search"
                                value={customerFilter}
                                onChange={(event) => setCustomerFilter(event.target.value)}
                                placeholder="Naziv kupca ili lokacije"
                                className="w-full rounded-xl border border-dust-grey py-2.5 pl-10 pr-3 outline-none focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20"
                            />
                        </label>
                        <select
                            aria-label="Status trebovanja"
                            value={statusFilter}
                            onChange={(event) => updateStatusFilter(event.target.value as RequisitionStatus | "ALL")}
                            className="rounded-xl border border-dust-grey px-3 py-2.5 text-graphite outline-none focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20"
                        >
                            <option value="ALL">Svi statusi</option>
                            {availableStatuses.map((status) => (
                                <option key={status} value={status}>
                                    {REQUISITION_STATUS_LABELS[status]}
                                </option>
                            ))}
                        </select>
                        <button
                            type="button"
                            disabled={!filtersActive}
                            onClick={() => {
                                setCustomerFilter("");
                                updateStatusFilter("ALL");
                            }}
                            className="inline-flex items-center justify-center gap-2 rounded-xl border border-dust-grey px-4 py-2.5 font-semibold text-yale-blue transition hover:border-stormy-teal hover:bg-yale-blue/5 disabled:cursor-not-allowed disabled:opacity-45"
                        >
                            <X className="h-4 w-4" />
                            Očisti filtere
                        </button>
                    </section>
                )}
                {error && <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-red-700">{error}<button type="button" onClick={query.retry} className="ml-3 font-bold underline">Pokušaj ponovo</button></p>}
                {loading ? <p className="mt-8 text-center text-graphite/60">Učitavanje trebovanja...</p> : error ? null : <>
                    <RequisitionSummaryCards requisitionCount={totalCount} gratisCount={gratisCount} />
                    <RequisitionTable
                        requisitions={filteredRequisitions}
                        period={selectedPeriod}
                        showSalesRep={user.role === "MANAGER" || user.role === "CEO"}
                        onSelect={(item) => setSelection({ id: item.id, context: query.context })}
                    />
                </>}
            </main>

            {selectedRequisition && (
                <RequisitionDetailsPanel key={`${selectedRequisition.id}:${selectedRequisition.revision}`}
                    requisition={selectedRequisition}
                    onClose={() => setSelection(null)}
                    onEdit={() => navigate(APP_ROUTES.editRequisition(selectedRequisition.id))}
                    onStatusChange={async (status, note) => {
                        await requisitionApi.updateStatus(selectedRequisition.id, status, note, selectedRequisition.revision);
                        query.retry();
                    }}
                />
            )}
        </AppLayout>
    );
}
