import { useCallback } from "react";
import { Link } from "react-router-dom";
import { APP_ROUTES } from "../../../app/routes";
import { useSessionRequest } from "../../hooks/use_session_request";
import { useAuth } from "../../../features/auth/hooks/useAuth";
import { riskApi } from "../../../features/ai/apis/risk_api";

type RiskOverviewProps = { salesRepId?: number | null; managerId?: number | null };

function Metric({ label, value, help }: { label: string; value: string | number; help?: string }) {
  return (
    <div className="rounded-xl bg-slate-50 p-3">
      <p className="text-sm text-graphite/70">{label}</p>
      <p className="text-xl font-bold text-yale-blue">{value}</p>
      {help && <p className="mt-1 text-xs text-graphite/60">{help}</p>}
    </div>
  );
}

export function RiskOverview({ salesRepId, managerId }: RiskOverviewProps) {
  const { user, hasPermission } = useAuth();
  const params = new URLSearchParams();
  if (salesRepId) params.set("salesRepId", String(salesRepId));
  if (managerId && user?.role === "CEO") params.set("managerId", String(managerId));
  const key = params.toString();
  const load = useCallback((signal: AbortSignal) => riskApi.summary(key, signal), [key]);
  const query = useSessionRequest(
    `risk-summary:${key}`,
    load,
    Boolean(user && user.role !== "BOOKKEEPER" && hasPermission("customers.read")),
  );
  if (!user || user.role === "BOOKKEEPER" || !hasPermission("customers.read")) return null;
  return (
    <section className="my-6 rounded-2xl border border-dust-grey bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-bold text-yale-blue">AI pregled rizika</h2>
        <Link to={`${APP_ROUTES.ai}?${key}`} className="text-sm font-semibold text-yale-blue underline">
          Prikaži kupce
        </Link>
      </div>
      {query.loading && <p role="status">Učitavanje AI pregleda...</p>}
      {query.error && <p role="alert">{query.error} <button onClick={query.reload}>Pokušaj ponovo</button></p>}
      {query.data && (
        <>
          <div className="my-4 grid grid-cols-2 gap-3 lg:grid-cols-5">
            <Metric label="Ukupno kupaca" value={query.data.total} />
            <Metric label="Visok rizik" value={query.data.distribution.HIGH ?? 0} />
            <Metric label="Srednji rizik" value={query.data.distribution.MEDIUM ?? 0} />
            <Metric label="Nizak rizik" value={query.data.distribution.LOW ?? 0} />
            <Metric label="Nedovoljno podataka" value={query.data.distribution.INSUFFICIENT_DATA ?? 0} />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Metric
              label="Udeo visokog rizika"
              value={query.data.highRiskPercentage === null
                ? "Nema procena"
                : `${query.data.highRiskPercentage.toFixed(1)}%`}
              help="Među kupcima sa dovoljno podataka"
            />
            <Metric
              label="Povećan rizik"
              value={query.data.increased}
              help="Kupci koji su prešli u višu kategoriju rizika"
            />
          </div>
          {query.data.teams.length > 0 && (
            <ul className="mt-4">
              {query.data.teams.map((team) => (
                <li key={team.manager_id ?? "none"}>
                  <Link className="underline" to={`${APP_ROUTES.ai}?managerId=${team.manager_id ?? ""}`}>
                    Tim {team.manager_name?.trim() || "bez menadžera"}
                  </Link>
                  : {team.high}/{team.customers} visok rizik
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
