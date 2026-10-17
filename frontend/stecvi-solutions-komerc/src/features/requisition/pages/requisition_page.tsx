import { useEffect, useMemo, useState } from "react";
import { Plus, Search, X } from "lucide-react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { PERMISSIONS } from "../../../roles/permissions";
import { useAuth } from "../../auth/hooks/useAuth";
import { RequisitionDetailsPanel } from "../components/requisition_details_panel";
import { PeriodSelector } from "../../../shared/components/period_selector";
import { RequisitionSummaryCards } from "../components/requisition_summary_cards";
import { RequisitionTable } from "../components/requisition_table";
import type { RequisitionListItem, RequisitionStatus } from "../types/requisition.types";
import { requisitionApi } from "../requisition_api";
import { REQUISITION_STATUS_LABELS } from "../data/requisition.data";

const isRequisitionStatus = (value: string | null): value is RequisitionStatus =>
    value !== null && Object.prototype.hasOwnProperty.call(REQUISITION_STATUS_LABELS, value);

export default function RequisitionPage() {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const { user, hasPermission } = useAuth();
    const [selectedDate, setSelectedDate] = useState(() => {
        const current = new Date();
        return new Date(current.getFullYear(), current.getMonth(), 1);
    });
    const [selectedRequisition, setSelectedRequisition] = useState<RequisitionListItem | null>(null);
    const [requisitions, setRequisitions] = useState<RequisitionListItem[]>([]);
    const [totalCount, setTotalCount] = useState(0);
    const [gratisCount, setGratisCount] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [customerFilter, setCustomerFilter] = useState("");
    const [statusFilter, setStatusFilter] = useState<RequisitionStatus | "ALL">(() => {
        const requestedStatus = searchParams.get("status");
        return isRequisitionStatus(requestedStatus) ? requestedStatus : "ALL";
    });
    const updateStatusFilter = (status: RequisitionStatus | "ALL") => {
        setStatusFilter(status);
        setSearchParams((current) => {
            const next = new URLSearchParams(current);
            if (status === "ALL") next.delete("status");
            else next.set("status", status);
            return next;
        }, { replace: true });
    };
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

    useEffect(() => {
        if (!user) return;
        let active = true;
        requisitionApi.getMonthlyRequisition(selectedDate.getFullYear(), selectedDate.getMonth() + 1)
            .then((result) => {
                if (!active) return;
                setRequisitions(result.requisitions);
                setTotalCount(result.totalCount);
                setGratisCount(result.gratisCount);
            })
            .catch((requestError: unknown) => {
                if (active) setError(requestError instanceof Error ? requestError.message : "Trebovanja nisu učitana.");
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [selectedDate, user]);

    if (!user) return <Navigate to="/login" replace />;
    if (!hasPermission(PERMISSIONS.REQUISITIONS_READ)) return <Navigate to="/" replace />;

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
                        <button type="button" onClick={() => navigate("/trebovanja/novo")} className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white shadow-sm transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2">
                            <Plus className="h-5 w-5" />
                            Novo trebovanje
                        </button>
                    )}
                </header>

                <PeriodSelector value={selectedDate} onChange={(date) => {
                    setLoading(true);
                    setError("");
                    setSelectedRequisition(null);
                    setSelectedDate(date);
                }} />
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
                {error && <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-red-700">{error}</p>}
                {loading ? <p className="mt-8 text-center text-graphite/60">Učitavanje trebovanja...</p> : <>
                    <RequisitionSummaryCards requisitionCount={totalCount} gratisCount={gratisCount} />
                    <RequisitionTable
                        requisitions={filteredRequisitions}
                        period={selectedPeriod}
                        showSalesRep={user.role === "MANAGER" || user.role === "CEO"}
                        onSelect={setSelectedRequisition}
                    />
                </>}
            </main>

            {selectedRequisition && (
                <RequisitionDetailsPanel
                    requisition={selectedRequisition}
                    onClose={() => setSelectedRequisition(null)}
                    onEdit={() => navigate(`/trebovanja/${selectedRequisition.id}/izmena`)}
                    onStatusChange={async (status, note) => {
                        const updated = await requisitionApi.updateStatus(selectedRequisition.id, status, note);
                        setSelectedRequisition(updated);
                        setRequisitions((current) => current.map((item) => item.id === updated.id ? updated : item));
                    }}
                />
            )}
        </AppLayout>
    );
}
