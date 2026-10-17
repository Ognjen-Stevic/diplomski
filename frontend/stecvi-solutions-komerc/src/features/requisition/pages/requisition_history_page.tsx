import { useEffect, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { Navigate, useNavigate, useParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { PERMISSIONS } from "../../../roles/permissions";
import { PeriodSelector } from "../../../shared/components/period_selector";
import { useAuth } from "../../auth/hooks/useAuth";
import { RequisitionDetailsPanel } from "../components/requisition_details_panel";
import { RequisitionTable } from "../components/requisition_table";
import { requisitionApi } from "../requisition_api";
import type { RequisitionListItem } from "../types/requisition.types";

export default function RequisitionHistoryPage() {
    const navigate = useNavigate();
    const { customerId: customerIdParam } = useParams();
    const { user, hasPermission } = useAuth();
    const customerId = Number(customerIdParam);
    const validCustomerId = Number.isInteger(customerId) && customerId > 0;
    const [selectedDate, setSelectedDate] = useState(() => {
        const current = new Date();
        return new Date(current.getFullYear(), current.getMonth(), 1);
    });
    const [requisitions, setRequisitions] = useState<RequisitionListItem[]>([]);
    const [selectedRequisition, setSelectedRequisition] = useState<RequisitionListItem | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const selectedPeriod = new Intl.DateTimeFormat("sr-Latn-RS", {
        month: "long",
        year: "numeric",
    }).format(selectedDate);

    useEffect(() => {
        if (!user || !validCustomerId || !hasPermission(PERMISSIONS.REQUISITIONS_READ)) return;

        let active = true;
        requisitionApi.getMonthlyCustomerRequisitions(
            customerId,
            selectedDate.getFullYear(),
            selectedDate.getMonth() + 1,
        )
            .then((result) => { if (active) setRequisitions(result.requisitions); })
            .catch((requestError: unknown) => {
                if (active) {
                    setRequisitions([]);
                    setError(requestError instanceof Error
                        ? requestError.message
                        : "Istorija trebovanja nije učitana.");
                }
            })
            .finally(() => { if (active) setLoading(false); });

        return () => { active = false; };
    }, [customerId, hasPermission, selectedDate, user, validCustomerId]);

    if (!user) return <Navigate to="/login" replace />;
    if (!hasPermission(PERMISSIONS.REQUISITIONS_READ) || !validCustomerId) {
        return <Navigate to="/kupci" replace />;
    }

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <button
                    type="button"
                    onClick={() => navigate("/kupci")}
                    className="inline-flex items-center gap-2 text-sm font-semibold text-yale-blue transition hover:text-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                >
                    <ArrowLeft className="h-4 w-4" />
                    Nazad na kupce
                </button>

                <header className="mt-4 border-b border-graphite/10 pb-5">
                    <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Kupac</p>
                    <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">
                        Istorija trebovanja
                    </h1>
                    <p className="mt-2 text-sm text-graphite/60">
                        Pregled trebovanja izabranog kupca po mesecu i godini.
                    </p>
                </header>

                <PeriodSelector value={selectedDate} onChange={(date) => {
                    setLoading(true);
                    setError("");
                    setSelectedRequisition(null);
                    setSelectedDate(date);
                }} />

                {error && (
                    <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-red-700">{error}</p>
                )}
                {loading ? (
                    <p className="mt-8 text-center text-graphite/60">Učitavanje istorije trebovanja...</p>
                ) : (
                    <RequisitionTable
                        requisitions={requisitions}
                        period={selectedPeriod}
                        showSalesRep={user.role === "MANAGER" || user.role === "CEO"}
                        onSelect={setSelectedRequisition}
                    />
                )}
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
