import { useMonthlyRequisitions } from "../hooks/use_requisition_data";
import { useRequisitionNavigation } from "../hooks/use_requisition_navigation";
import { APP_ROUTES } from "../../../app/routes";
import { useState } from "react";
import { ArrowLeft } from "lucide-react";
import { Navigate, useNavigate, useParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { PERMISSIONS } from "../../../roles/permissions";
import { PeriodSelector } from "../../../shared/components/period_selector";
import { useAuth } from "../../auth/hooks/useAuth";
import { RequisitionDetailsPanel } from "../components/requisition_details_panel";
import { RequisitionTable } from "../components/requisition_table";
import { requisitionApi } from "../requisition_api";

export default function RequisitionHistoryPage() {
    const navigate = useNavigate();
    const { customerId: customerIdParam } = useParams();
    const { user, hasPermission } = useAuth();
    const customerId = Number(customerIdParam);
    const validCustomerId = Number.isInteger(customerId) && customerId > 0;
    const navigation = useRequisitionNavigation();
    const selectedDate = navigation.date;
    const query = useMonthlyRequisitions(navigation.year, navigation.month, customerId);
    const requisitions = query.data?.requisitions ?? [];
    const { loading, error } = query;
    const [selection, setSelection] = useState<{ id: number; context: object } | null>(null);
    const selectedRequisition = selection?.context === query.context
        ? requisitions.find((item) => item.id === selection.id) ?? null : null;
    const selectedPeriod = new Intl.DateTimeFormat("sr-Latn-RS", {
        month: "long",
        year: "numeric",
    }).format(selectedDate);

    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (!hasPermission(PERMISSIONS.REQUISITIONS_READ) || !validCustomerId) {
        return <Navigate to={APP_ROUTES.customers} replace />;
    }

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <button
                    type="button"
                    onClick={() => navigate(APP_ROUTES.customers)}
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

                <PeriodSelector value={selectedDate} onChange={(date) => navigation.update(date, navigation.status)} />

                {error && (
                    <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-red-700">{error}<button type="button" onClick={query.retry} className="ml-3 font-bold underline">Pokušaj ponovo</button></p>
                )}
                {loading ? (
                    <p className="mt-8 text-center text-graphite/60">Učitavanje istorije trebovanja...</p>
                ) : error ? null : (
                    <RequisitionTable
                        requisitions={requisitions}
                        period={selectedPeriod}
                        showSalesRep={user.role === "MANAGER" || user.role === "CEO"}
                        onSelect={(item) => setSelection({ id: item.id, context: query.context })}
                    />
                )}
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
