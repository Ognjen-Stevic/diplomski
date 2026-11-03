import { RiskOverview } from "../../ai/risk_components";
import { ArrowLeft } from "lucide-react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { PERMISSIONS } from "../../../roles/permissions";
import { useAuth } from "../../auth/hooks/useAuth";
import { TeamComparisonSection } from "../components/team_comparison_section";
import { TeamMembersOverview } from "../components/team_members_overview";
import { useTeamSummary } from "../hooks/use_team_summary";

const reportDestination = (parameters: Record<string, number>) => {
    const search = new URLSearchParams();
    Object.entries(parameters).forEach(([name, value]) => search.set(name, String(value)));
    return { pathname: APP_ROUTES.reports, search: `?${search.toString()}` };
};

export default function TeamPage() {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const { user, hasPermission } = useAuth();
    const managerIdValue = Number(searchParams.get("managerId"));
    const selectedManagerId = Number.isInteger(managerIdValue) && managerIdValue > 0
        ? managerIdValue
        : null;
    const hasSupportedRole = user?.role === "MANAGER" || user?.role === "CEO";
    const canReadEmployees = hasPermission(PERMISSIONS.EMPLOYEES_READ);
    const canReadSalesReports = hasPermission(PERMISSIONS.SALES_REPORTS_READ);
    const managerOverview = user?.role === "CEO" && selectedManagerId === null;
    const summary = useTeamSummary({
        enabled: Boolean(user && hasSupportedRole && canReadEmployees),
        mode: managerOverview ? "managers" : "team",
        managerId: user?.role === "CEO" ? selectedManagerId : null,
    });

    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (!hasSupportedRole || !canReadEmployees) {
        return <Navigate to={APP_ROUTES.home} replace />;
    }

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <RiskOverview managerId={selectedManagerId}/>
                {user.role === "CEO" && selectedManagerId !== null && (
                    <button
                        type="button"
                        onClick={() => setSearchParams({}, { replace: true })}
                        className="mb-5 inline-flex items-center gap-2 text-sm font-semibold text-yale-blue transition hover:text-stormy-teal"
                    >
                        <ArrowLeft className="h-4 w-4" />
                        Nazad na pregled menadžera
                    </button>
                )}

                <header>
                    <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Upravljanje timom</p>
                    <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">
                        {managerOverview ? "Moj Tim" : "Komercijalisti"}
                    </h1>
                    <p className="mt-2 text-sm text-graphite/60">
                        {managerOverview
                            ? "Pregled menadžera i učinka njihovih prodajnih timova."
                            : "Pregled članova tima i poređenje njihovih prodajnih rezultata."}
                    </p>
                </header>

                <TeamMembersOverview
                    members={summary.members}
                    loading={summary.loading}
                    error={summary.error}
                    variant={managerOverview ? "managers" : "team"}
                    onRetry={summary.retry}
                    onAction={(memberId) => {
                        if (managerOverview) {
                            setSearchParams({ managerId: String(memberId) });
                            return;
                        }
                        navigate(reportDestination({
                            salesRepId: memberId,
                            ...(user.role === "CEO" && selectedManagerId !== null
                                ? { managerId: selectedManagerId }
                                : {}),
                        }));
                    }}
                    onViewReport={managerOverview
                        ? (managerId) => navigate(reportDestination({ managerId }))
                        : undefined}
                />

                {canReadSalesReports && (
                    <TeamComparisonSection
                        enabled
                        managerId={user.role === "CEO" ? selectedManagerId : null}
                        variant={managerOverview ? "managers" : "sales-reps"}
                    />
                )}
            </main>
        </AppLayout>
    );
}
