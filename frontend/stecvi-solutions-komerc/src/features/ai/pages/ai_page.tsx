import { AiCustomerFilters } from "../components/ai_customer_filters";
import { useCallback } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { useAuth } from "../../auth/hooks/useAuth";
import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { riskApi } from "../apis/risk_api";
import { CustomerAiPanel } from "../../../shared/components/ai/customer_ai_panel";
import { RiskBadge } from "../../../shared/components/ai/risk_badge";
import { RiskOverview } from "../../../shared/components/ai/risk_overview";
import { Pagination } from "../../../shared/components/pagination";

export default function AiPage() {
  const { user, hasPermission } = useAuth();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const listParams = new URLSearchParams(params);
  listParams.delete("customerId");
  const key = listParams.toString();
  const load = useCallback(
    (signal: AbortSignal) => riskApi.list(key, signal),
    [key],
  );
  const query = useSessionRequest(
    `ai-list:${key}`,
    load,
    Boolean(user && user.role !== "BOOKKEEPER" && hasPermission("customers.read")),
  );
  if (!user || user.role === "BOOKKEEPER" || !hasPermission("customers.read"))
    return <Navigate to={APP_ROUTES.home} replace />;
  const change = (name: string, value: string) => {
    const next = new URLSearchParams(params);
    next.delete("page");
    if (value) next.set(name, value);
    else next.delete(name);
    setParams(next);
  };
  const customerId = Number(params.get("customerId"));
  const selectedCustomer = query.data?.content.find((customer) => customer.customerId === customerId);
  const openAnalysis = (id: number) => {
    const next = new URLSearchParams(params);
    next.set("customerId", String(id));
    setParams(next);
  };
  const closeAnalysis = () => {
    const next = new URLSearchParams(params);
    next.delete("customerId");
    setParams(next);
  };
  return (
    <AppLayout>
      <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
        <h1 className="text-3xl font-bold text-yale-blue">AI Analytics</h1>
        <RiskOverview
          salesRepId={Number(params.get("salesRepId")) || null}
          managerId={Number(params.get("managerId")) || null}
        />
        <AiCustomerFilters
          search={params.get("search") ?? ""}
          risk={params.get("risk") ?? ""}
          onSearch={(value) => change("search", value)}
          onRiskChange={(value) => change("risk", value)}
        />
        {query.loading && <p role="status">Učitavanje kupaca...</p>}
        {query.error && (
          <p role="alert">
            {query.error} <button onClick={query.reload}>Pokušaj ponovo</button>
          </p>
        )}
        {query.data && (
          <>
            <ul className="my-4 grid gap-3 md:grid-cols-2">
              {query.data.content.map((a) => (
                <li key={a.customerId}>
                  <button
                    type="button"
                    onClick={() => openAnalysis(a.customerId)}
                    className="group w-full rounded-xl border border-dust-grey bg-white p-4 text-left transition hover:border-stormy-teal hover:shadow-md focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                    aria-label={`Otvori AI analizu za ${a.customerName}`}
                  >
                    <span className="flex items-start justify-between gap-4">
                      <span className="min-w-0">
                        <span className="block truncate text-lg font-bold text-yale-blue">{a.customerName}</span>
                        <span className="mt-2 block"><RiskBadge level={a.level} /></span>
                      </span>
                      <ChevronRight className="mt-1 h-5 w-5 shrink-0 text-graphite/40 transition group-hover:translate-x-1 group-hover:text-stormy-teal" aria-hidden="true" />
                    </span>
                    <span className="mt-3 block text-sm text-graphite/65">
                      Poslednja procena: {a.at ? new Date(a.at).toLocaleString("sr-Latn-RS") : "Nije izvršena"}
                    </span>
                    {a.stale && <span className="mt-1 block text-sm font-semibold text-amber-700">Potrebno osvežavanje procene</span>}
                    <span className="mt-3 block text-sm text-graphite">{a.recommendation}</span>
                  </button>
                </li>
              ))}
            </ul>
            {query.data.content.length === 0 && (
              <p>Nema kupaca za izabrane filtere.</p>
            )}
            <Pagination
              currentPage={page + 1}
              totalPages={Math.ceil(query.data.total / query.data.size)}
              onChange={(nextPage) => {
                const next = new URLSearchParams(params);
                next.set("page", String(nextPage - 1));
                setParams(next);
              }}
            />
          </>
        )}
        {customerId > 0 && (
          <CustomerAiPanel
            key={customerId}
            customerId={customerId}
            customerName={selectedCustomer?.customerName}
            onClose={closeAnalysis}
          />
        )}
      </main>
    </AppLayout>
  );
}
