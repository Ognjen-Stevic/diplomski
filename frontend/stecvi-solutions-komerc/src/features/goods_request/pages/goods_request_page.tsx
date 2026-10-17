import { useCallback, useMemo, useState } from "react";
import { Plus } from "lucide-react";
import { Navigate, useNavigate } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { MonthlyRequestTable } from "../../../shared/components/monthly_request_table";
import type { MonthlyRequestRecord } from "../../../shared/types/monthly_request";
import { PERMISSIONS } from "../../../roles/permissions";
import { canApproveGoodsRequest, canCreateGoodsRequest } from "../../../roles/access_control";
import { PeriodSelector } from "../../../shared/components/period_selector";
import { useAuth } from "../../auth/hooks/useAuth";
import { goodsRequestApi } from "../goods_request_api";
import { useMonthlyGoodsRequests } from "../hooks/use_monthly_goods_requests";
import { GoodsRequestStatusDialog } from "../components/goods_request_status_dialog";
import type { GoodsRequestRecord, ProcessGoodsRequestStatus } from "../types/goods_request.types";
import { refreshDashboardAlerts } from "../../dashboard/alert_api";

export default function GoodsRequestPage() {
    const navigate = useNavigate();
    const { user, hasPermission } = useAuth();
    const canCreate = canCreateGoodsRequest(user?.role, hasPermission);
    const canApprove = canApproveGoodsRequest(user?.role, hasPermission);
    const canRead = Boolean(user && hasPermission(PERMISSIONS.GOODS_REQUESTS_READ));
    const { selectedDate, setSelectedDate, records, loading, error, reload } =
        useMonthlyGoodsRequests(canRead, user ? `${user.id}:${user.role}` : "");
    const [pendingDecision, setPendingDecision] = useState<{
        request: GoodsRequestRecord;
        status: ProcessGoodsRequestStatus;
    } | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [processingError, setProcessingError] = useState("");
    const tableRecords = useMemo<MonthlyRequestRecord[]>(() => records.map((request) => ({
        id: request.id,
        salesRep: request.salesRep,
        items: request.items.map((item) => ({ ...item, quantity: Number(item.quantity) })),
        note: request.note,
        date: request.createdAt,
        status: request.status,
    })), [records]);
    const recordsById = useMemo(
        () => new Map(records.map((request) => [request.id, request])),
        [records],
    );

    const closeDecision = useCallback(() => {
        if (!submitting) {
            setPendingDecision(null);
            setProcessingError("");
        }
    }, [submitting]);

    const processRequest = async () => {
        if (!pendingDecision || submitting) return;
        setSubmitting(true);
        setProcessingError("");
        try {
            await goodsRequestApi.updateStatus(pendingDecision.request.id, pendingDecision.status);
            setPendingDecision(null);
            reload();
            refreshDashboardAlerts();
        } catch (requestError) {
            setProcessingError(requestError instanceof Error ? requestError.message : "Zahtev nije obrađen.");
        } finally {
            setSubmitting(false);
        }
    };

    const openDecision = (recordId: number, status: ProcessGoodsRequestStatus) => {
        const request = recordsById.get(recordId);
        if (!request) return;
        setProcessingError("");
        setPendingDecision({ request, status });
    };

    if (!user) return <Navigate to="/login" replace />;
    if (!hasPermission(PERMISSIONS.GOODS_REQUESTS_READ)) return <Navigate to="/" replace />;

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Magacin</p>
                        <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">Zahtevi za robu</h1>
                        <p className="mt-2 text-sm text-graphite/60">Pregled zahtevanih proizvoda i njihovog statusa.</p>
                    </div>
                    {canCreate && (
                        <button
                            type="button"
                            onClick={() => navigate(APP_ROUTES.newGoodsRequest)}
                            className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white"
                        >
                            <Plus className="h-5 w-5" />
                            Novi zahtev
                        </button>
                    )}
                </header>
                <PeriodSelector
                    value={selectedDate}
                    onChange={setSelectedDate}
                />
                {error && (
                    <div role="alert" className="mt-5 flex flex-col gap-3 rounded-xl bg-red-50 px-4 py-3 text-red-700 sm:flex-row sm:items-center sm:justify-between">
                        <span>{error}</span>
                        <button type="button" onClick={reload} className="font-semibold underline underline-offset-2">Pokušaj ponovo</button>
                    </div>
                )}
                {loading ? (
                    <p className="mt-8 text-center text-graphite/60">Učitavanje zahteva...</p>
                ) : (
                    <MonthlyRequestTable
                        records={tableRecords}
                        dateColumnLabel="Datum kreiranja"
                        showStatus
                        showSalesRepColumn={user.role === "MANAGER" || user.role === "CEO" || user.role === "BOOKKEEPER"}
                        renderActions={canApprove ? (record) => recordsById.get(record.id)?.hasPendingItems ? (
                            <div className="flex justify-end gap-2">
                                <button type="button" onClick={() => openDecision(record.id, "APPROVED")} className="rounded-lg bg-emerald-700 px-3 py-2 text-sm font-semibold text-white hover:bg-emerald-800 focus:outline-none focus:ring-2 focus:ring-emerald-600/40">
                                    Odobri
                                </button>
                                <button type="button" onClick={() => openDecision(record.id, "REJECTED")} className="rounded-lg bg-red-700 px-3 py-2 text-sm font-semibold text-white hover:bg-red-800 focus:outline-none focus:ring-2 focus:ring-red-600/40">
                                    Odbij
                                </button>
                            </div>
                        ) : <span className="text-sm text-graphite/45">Obrađeno</span> : undefined}
                        emptyMessage="Nema zahteva za izabrani period."
                    />
                )}
            </main>
            {pendingDecision && (
                <GoodsRequestStatusDialog
                    request={pendingDecision.request}
                    status={pendingDecision.status}
                    submitting={submitting}
                    error={processingError}
                    onCancel={closeDecision}
                    onConfirm={() => void processRequest()}
                />
            )}
        </AppLayout>
    );
}
