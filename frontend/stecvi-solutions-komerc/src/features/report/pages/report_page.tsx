import { useMemo, useState } from "react";
import { Navigate, useSearchParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { PERMISSIONS } from "../../../roles/permissions";
import { useAuth } from "../../auth/hooks/useAuth";
import { useTeamSummary } from "../../sales_team/hooks/use_team_summary";
import type {
    ManagerTeamSummary,
    TeamMemberSummary,
} from "../../sales_team/types/sales_team.types";
import { GratisYtdReport } from "../components/gratis_ytd_report";
import { MonthlyGratisReport } from "../components/monthly_gratis_report";
import { MonthlyReport } from "../components/monthly_report";
import { YtdReport } from "../components/ytd_report";
import type { ReportType } from "../types/sales_report.types";

const REPORT_TABS: Array<{ id: ReportType; label: string; description: string }> = [
    { id: "monthly", label: "Mesečni izveštaj", description: "Rezultati za izabrani mesec" },
    { id: "ytd", label: "YTD izveštaj", description: "Rezultati od početka godine" },
    { id: "gratis-monthly", label: "Mesečni gratis", description: "Gratis proizvodi za izabrani mesec" },
    { id: "gratis-ytd", label: "Gratis YTD", description: "Gratis proizvodi po razlogu" },
];

export default function ReportPage() {
    const { user, hasPermission } = useAuth();
    const [searchParams, setSearchParams] = useSearchParams();
    const [activeReport, setActiveReport] = useState<ReportType>("monthly");
    const salesRepIdValue = Number(searchParams.get("salesRepId"));
    const selectedSalesRepId = Number.isInteger(salesRepIdValue) && salesRepIdValue > 0
        ? salesRepIdValue
        : null;
    const managerIdValue = Number(searchParams.get("managerId"));
    const selectedManagerId = Number.isInteger(managerIdValue) && managerIdValue > 0
        ? managerIdValue
        : null;
    const canReadEmployees = hasPermission(PERMISSIONS.EMPLOYEES_READ);
    const isManagerView = user?.role === "MANAGER"
        || (user?.role === "CEO" && selectedManagerId !== null);
    const teamSummary = useTeamSummary({
        enabled: Boolean(canReadEmployees && isManagerView),
        mode: "team",
        managerId: user?.role === "CEO" ? selectedManagerId : null,
    });
    const managersOverview = useTeamSummary({
        enabled: Boolean(canReadEmployees && user?.role === "CEO" && selectedManagerId !== null),
        mode: "managers",
    });
    const teamMembers = teamSummary.members.filter(
        (member): member is TeamMemberSummary => "salesRepId" in member,
    );
    const managerNames = useMemo(() => Object.fromEntries(
        managersOverview.members
            .filter((manager): manager is ManagerTeamSummary => "managerId" in manager)
            .map((manager) => [
                manager.managerId,
                `${manager.firstName} ${manager.lastName}`,
            ]),
    ), [managersOverview.members]);

    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (!hasPermission(PERMISSIONS.SALES_REPORTS_READ)) {
        return <Navigate to={APP_ROUTES.home} replace />;
    }

    const activeReportComponent = activeReport === "monthly"
        ? <MonthlyReport salesRepId={selectedSalesRepId} managerId={selectedManagerId} />
        : activeReport === "ytd"
            ? <YtdReport salesRepId={selectedSalesRepId} managerId={selectedManagerId} />
            : activeReport === "gratis-monthly"
                ? <MonthlyGratisReport salesRepId={selectedSalesRepId} managerId={selectedManagerId} />
                : <GratisYtdReport salesRepId={selectedSalesRepId} managerId={selectedManagerId} />;

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <header>
                    <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Prodajni učinak</p>
                    <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">Izveštaji</h1>
                    <p className="mt-2 text-sm text-graphite/60">
                        {isManagerView ? "Menadžer" : "Komercijalista"}: <strong className="text-graphite">
                            {(selectedManagerId && managerNames[selectedManagerId]) ?? user.name}
                        </strong>
                    </p>
                </header>

                <nav className="mt-7 grid grid-cols-2 gap-3 xl:grid-cols-4" aria-label="Vrsta izveštaja">
                    {REPORT_TABS.map((tab) => {
                        const active = activeReport === tab.id;
                        return (
                            <button
                                key={tab.id}
                                type="button"
                                aria-pressed={active}
                                onClick={() => setActiveReport(tab.id)}
                                className={`rounded-2xl border px-5 py-4 text-left transition focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 ${active
                                    ? "border-yale-blue bg-yale-blue text-white shadow-sm"
                                    : "border-dust-grey bg-white text-graphite hover:border-stormy-teal hover:bg-stormy-teal/[0.03]"}`}
                            >
                                <span className="block font-bold">{tab.label}</span>
                                <span className={`mt-1 block text-xs ${active ? "text-white/70" : "text-graphite/50"}`}>
                                    {tab.description}
                                </span>
                            </button>
                        );
                    })}
                </nav>

                {isManagerView && canReadEmployees && (
                    <section className="mt-6 rounded-2xl border border-dust-grey bg-white p-4 sm:p-5">
                        <label htmlFor="sales-representative" className="block text-sm font-bold text-yale-blue">
                            Komercijalista
                        </label>
                        <select
                            id="sales-representative"
                            value={selectedSalesRepId ?? ""}
                            disabled={teamSummary.loading}
                            onChange={(event) => {
                                const salesRepId = event.target.value ? Number(event.target.value) : null;
                                const nextSearchParams = new URLSearchParams(searchParams);
                                if (salesRepId === null) nextSearchParams.delete("salesRepId");
                                else nextSearchParams.set("salesRepId", String(salesRepId));
                                setSearchParams(nextSearchParams, { replace: true });
                            }}
                            className="mt-2 w-full rounded-xl border border-dust-grey bg-white px-3 py-2.5 text-sm text-graphite focus:border-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 disabled:opacity-50 sm:max-w-md"
                        >
                            <option value="">Svi članovi tima</option>
                            {teamMembers.map((member) => (
                                <option key={member.salesRepId} value={member.salesRepId}>
                                    {member.firstName} {member.lastName}
                                </option>
                            ))}
                        </select>
                        {teamSummary.error && (
                            <p role="alert" className="mt-2 text-sm text-red-700">{teamSummary.error}</p>
                        )}
                    </section>
                )}

                {activeReportComponent}
            </main>
        </AppLayout>
    );
}
