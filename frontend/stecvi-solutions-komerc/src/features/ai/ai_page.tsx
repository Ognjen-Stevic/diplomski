import { AiCustomerFilters } from "./components/ai_customer_filters";
import { useCallback } from "react";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import AppLayout from "../../app/layouts/app_layout";
import { APP_ROUTES } from "../../app/routes";
import { useAuth } from "../auth/hooks/useAuth";
import { useSessionRequest } from "../../shared/hooks/use_session_request";
import { riskApi } from "./risk_api";
import { AiAdminTools } from "./admin_tools";
import { RiskBadge, RiskOverview, CustomerAiAnalysis } from "./risk_components";

export default function AiPage() {
  const { user, hasPermission } = useAuth();
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const key = params.toString();
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
                <li
                  key={a.customerId}
                  className="rounded-xl border border-dust-grey bg-white p-4"
                >
                  <Link
                    className="font-bold text-yale-blue underline"
                    to={`${APP_ROUTES.customers}?search=${encodeURIComponent(a.customerName)}&customerId=${a.customerId}`}
                  >
                    {a.customerName}
                  </Link>{" "}
                  <RiskBadge level={a.level} />
                  <p>
                    {a.status} {a.stale ? "· Potrebno osvežavanje" : ""}
                  </p>
                  <p>{a.recommendation}</p>
                  <button
                    onClick={() => change("customerId", String(a.customerId))}
                    className="underline"
                  >
                    AI detalji
                  </button>
                </li>
              ))}
            </ul>
            {query.data.content.length === 0 && (
              <p>Nema kupaca za izabrane filtere.</p>
            )}
            <button
              disabled={page === 0}
              onClick={() => {
                const next = new URLSearchParams(params);
                next.set("page", String(page - 1));
                setParams(next);
              }}
            >
              Prethodna
            </button>
            <span className="mx-3">{page + 1}</span>
            <button
              disabled={(page + 1) * 25 >= query.data.total}
              onClick={() => {
                const next = new URLSearchParams(params);
                next.set("page", String(page + 1));
                setParams(next);
              }}
            >
              Sledeća
            </button>
          </>
        )}
        {customerId > 0 && (
          <CustomerAiAnalysis key={customerId} customerId={customerId} />
        )}
        <AiAdminTools />
      </main>
    </AppLayout>
  );
}
