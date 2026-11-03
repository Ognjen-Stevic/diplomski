import { RiskOverview } from "../../ai/risk_components";
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { PERMISSIONS } from "../../../roles/permissions";
import { useAuth } from "../../auth/hooks/useAuth";
import { DashboardAlerts } from "../components/alerts/dashboard_alerts";
import { CustomerSearch } from "../../customer/components/customer_search";
import { QuickActionCard } from "../components/quick_action_section/quick_action_card";
import { MonthlyStatsSection } from "../components/stats/monthly_stats_section";
import { useVisibleQuickActions } from "../hooks/use_visible_quick_actions";

export default function HomePage() {
    const navigate = useNavigate();
    const [search, setSearch] = useState("");
    const { user, hasPermission } = useAuth();
    const visibleQuickActions = useVisibleQuickActions();

    if (!user) return null;

    const isCeo = user.role === "CEO";
    const isBookkeeper = user.role === "BOOKKEEPER";
    const showCustomerSearch = !isCeo && hasPermission(PERMISSIONS.CUSTOMERS_READ);
    const showSalesStats = !isCeo
        && !isBookkeeper
        && hasPermission(PERMISSIONS.SALES_REPORTS_READ);
    const formattedDate = new Intl.DateTimeFormat("sr-Latn-RS", {
        weekday: "long",
        day: "numeric",
        month: "long",
        year: "numeric",
    }).format(new Date());

    const handleSearch = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const value = search.trim();
        navigate(
            value
                ? `${APP_ROUTES.customers}?search=${encodeURIComponent(value)}`
                : APP_ROUTES.customers,
        );
    };

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <section className="mb-6">
                    <p className="text-sm font-semibold capitalize text-stormy-teal">
                        {formattedDate}
                    </p>
                    <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">
                        Dobro došli, {user.name.split(" ")[0]}
                    </h1>
                </section>

                <RiskOverview />
                {showCustomerSearch && (
                    <CustomerSearch
                        value={search}
                        onChange={setSearch}
                        onSubmit={handleSearch}
                    />
                )}

                <section className={`my-8 ${isBookkeeper ? "" : "hidden lg:block"}`}>
                    <h2 className="mb-4 hidden text-xl font-bold text-yale-blue lg:block">
                        Brze akcije
                    </h2>
                    <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
                        {visibleQuickActions.map((action) => (
                            <QuickActionCard
                                key={action.route}
                                action={action}
                                className="hidden lg:flex"
                            />
                        ))}
                        {isBookkeeper && (
                            <MonthlyStatsSection mode="bookkeeper" embedded />
                        )}
                    </div>
                </section>

                {isCeo
                    ? <MonthlyStatsSection mode="ceo" />
                    : showSalesStats && <MonthlyStatsSection />}

                {!isCeo && <DashboardAlerts />}
            </main>
        </AppLayout>
    );
}
