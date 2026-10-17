import { useMemo, useState } from "react";
import { Plus } from "lucide-react";
import { Navigate, useNavigate } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { canAssignAdvertisingMaterial } from "../../../roles/access_control";
import { PERMISSIONS } from "../../../roles/permissions";
import { MonthlyRequestTable } from "../../../shared/components/monthly_request_table";
import type { MonthlyRequestRecord } from "../../../shared/types/monthly_request";
import { PeriodSelector } from "../../../shared/components/period_selector";
import { useAuth } from "../../auth/hooks/useAuth";
import { useAdvertisingMaterialHistory } from "../hooks/use_advertising_material_history";

export default function AdvertisingMaterialHistoryPage() {
    const navigate = useNavigate();
    const { user, hasPermission } = useAuth();
    const canViewHistory = hasPermission(PERMISSIONS.ADVERTISING_MATERIALS_READ);
    const canAssignMaterial = canAssignAdvertisingMaterial(user?.role, hasPermission);
    const [selectedDate, setSelectedDate] = useState(() => {
        const now = new Date();
        return new Date(now.getFullYear(), now.getMonth(), 1);
    });
    const history = useAdvertisingMaterialHistory(selectedDate, Boolean(user && canViewHistory));
    const records = useMemo<MonthlyRequestRecord[]>(() =>
        history.records.map((distribution) => ({
            id: distribution.id,
            salesRep: distribution.salesRep,
            customer: distribution.customer,
            items: [{
                name: distribution.materialName,
                quantity: distribution.quantity,
            }],
            note: distribution.note,
            date: distribution.distributedAt,
        })), [history.records]);

    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (!canViewHistory) return <Navigate to={APP_ROUTES.home} replace />;

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Podrška prodaji</p>
                        <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">Reklamni materijal</h1>
                        <p className="mt-2 text-sm text-graphite/60">Istorija reklamnog materijala dodeljenog kupcima.</p>
                    </div>
                    {canAssignMaterial && (
                        <button
                            type="button"
                            onClick={() => navigate(APP_ROUTES.newAdvertisingMaterialDistribution)}
                            className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white"
                        >
                            <Plus className="h-5 w-5" />
                            Nova dodela
                        </button>
                    )}
                </header>

                <PeriodSelector value={selectedDate} onChange={setSelectedDate} />

                {history.error && (
                    <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-red-700">
                        {history.error}
                    </p>
                )}
                {history.loading ? (
                    <p className="mt-8 text-center text-graphite/60">Učitavanje dodeljenog materijala...</p>
                ) : (
                    <MonthlyRequestTable
                        records={records}
                        dateColumnLabel="Datum kreiranja"
                        showCustomerColumn
                        showSalesRepColumn={user.role === "MANAGER" || user.role === "CEO"}
                        emptyMessage="Nema dodeljenog reklamnog materijala za izabrani period."
                    />
                )}
            </main>
        </AppLayout>
    );
}
