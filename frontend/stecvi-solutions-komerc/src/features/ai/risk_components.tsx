import { riskLabels, featureLabels } from "./risk_labels";
import { RiskTrends } from "./risk_trends";
import { useCallback, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useSessionRequest } from "../../shared/hooks/use_session_request";
import { useAuth } from "../auth/hooks/useAuth";
import { riskApi, type Level } from "./risk_api";
import { APP_ROUTES } from "../../app/routes";
import { SessionScope } from "../../shared/components/session_scope";

export function RiskBadge({ level }: { level: Level }) {
  return (
    <span
      className={`inline-block rounded-full px-3 py-1 text-xs font-bold ${level === "HIGH" ? "bg-red-50 text-red-700" : level === "MEDIUM" ? "bg-amber-50 text-amber-800" : level === "LOW" ? "bg-green-50 text-green-800" : "bg-dust-grey/40 text-graphite"}`}
    >
      {riskLabels[level]}
    </span>
  );
}

export function RiskOverview({
  salesRepId,
  managerId,
}: {
  salesRepId?: number | null;
  managerId?: number | null;
}) {
  const { user, hasPermission } = useAuth();
  const params = new URLSearchParams();
  if (salesRepId) params.set("salesRepId", String(salesRepId));
  if (managerId && user?.role === "CEO")
    params.set("managerId", String(managerId));
  const key = params.toString();
  const load = useCallback(
    (signal: AbortSignal) => riskApi.summary(key, signal),
    [key],
  );
  const query = useSessionRequest(
    `risk-summary:${key}`,
    load,
    Boolean(user && user.role !== "BOOKKEEPER" && hasPermission("customers.read")),
  );
  if (!user || user.role === "BOOKKEEPER" || !hasPermission("customers.read")) return null;
  return (
    <section className="my-6 rounded-2xl border border-dust-grey bg-white p-5">
      <Link
        to={`${APP_ROUTES.ai}?${key}`}
        className="font-bold text-yale-blue underline"
      >
        AI Analytics
      </Link>
      {query.loading && <p role="status">Učitavanje AI pregleda...</p>}
      {query.error && (
        <p role="alert">
          {query.error} <button onClick={query.reload}>Pokušaj ponovo</button>
        </p>
      )}
      {query.data && (
        <>
          <div className="my-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <p>
              Kupci: <strong>{query.data.total}</strong>
            </p>
            <p>
              Visok rizik: <strong>{query.data.distribution.HIGH ?? 0}</strong>
            </p>
            <p>
              Srednji rizik:{" "}
              <strong>{query.data.distribution.MEDIUM ?? 0}</strong>
            </p>
            <p>
              Rizik povećan: <strong>{query.data.increased}</strong>
            </p>
          </div>
          <p>
            Visok rizik među procenjenim kupcima:{" "}
            {query.data.highRiskPercentage === null
              ? "Nema validnih procena"
              : `${query.data.highRiskPercentage.toFixed(1)}%`}
          </p>
          <p>
            Istorijska prodaja kupcima visokog rizika (60 dana):{" "}
            {query.data.historicalHighRiskLiters.toFixed(1)} l
          </p>
          {query.data.teams.length > 0 && (
            <ul className="mt-3">
              {query.data.teams.map((t) => (
                <li key={t.manager_id ?? "none"}>
                  <Link
                    className="underline"
                    to={`${APP_ROUTES.ai}?managerId=${t.manager_id ?? ""}`}
                  >
                    Tim {t.manager_name?.trim() || "bez menadžera"}
                  </Link>
                  : {t.high}/{t.customers} visok rizik
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      <RiskTrends scope={key} />
    </section>
  );
}
export function CustomerAiAnalysis({ customerId }: { customerId: number }) {
  return (
    <SessionScope>
      <CustomerAiContent key={customerId} customerId={customerId} />
    </SessionScope>
  );
}
function CustomerAiContent({ customerId }: { customerId: number }) {
  const { user, hasPermission } = useAuth();
  const load = useCallback(
    (signal: AbortSignal) =>
      Promise.all([
        riskApi.detail(customerId, signal),
        riskApi.activities(customerId, signal),
      ]),
    [customerId],
  );
  const query = useSessionRequest(
    `customer-ai:${customerId}`,
    load,
    Boolean(user && user.role !== "BOOKKEEPER" && hasPermission("customers.read")),
  );
  const guard = useRef(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<{
    context: object;
    text: string;
  } | null>(null);
  const [outcome, setOutcome] = useState("");
  if (user?.role === "BOOKKEEPER" || !hasPermission("customers.read")) return null;
  return (
    <section className="my-5 rounded-xl border border-dust-grey p-4">
      <h3 className="font-bold text-yale-blue">AI analiza</h3>
      {query.loading && <p role="status">Učitavanje analize...</p>}
      {query.error && (
        <p role="alert">
          {query.error} <button onClick={query.reload}>Pokušaj ponovo</button>
        </p>
      )}
      {query.data && (
        <>
          <RiskBadge level={query.data[0].level} />
          <p>Status: {query.data[0].status}</p>
          <p>
            Procena:{" "}
            {query.data[0].at
              ? new Date(query.data[0].at).toLocaleString("sr-Latn-RS")
              : "Još nije izvršena"}
          </p>
          {query.data[0].stale && <p>Procena zahteva osvežavanje.</p>}
          <p>Model: {query.data[0].modelVersion ?? "Nema aktivnog modela"}</p>
          {query.data[0].features && (
            <dl className="my-3 grid grid-cols-2 gap-2">
              {Object.entries(query.data[0].features).map(([name, value]) => (
                <div key={name}>
                  <dt>{featureLabels[name] ?? name}</dt>
                  <dd>{value.toFixed(1)}</dd>
                </div>
              ))}
            </dl>
          )}
          {query.data[0].factors.length > 0 && (
            <h4 className="font-bold">Putanja Decision Tree modela</h4>
          )}
          <ul>
            {query.data[0].factors.map((factor, i) => (
              <li key={i}>
                {Object.entries(featureLabels).reduce(
                  (text, [key, label]) => text.replace(key, label),
                  factor,
                )}
              </li>
            ))}
          </ul>
          <p>{query.data[0].recommendation}</p>
          <h4 className="mt-4 font-bold">Evidentirane aktivnosti</h4>
          {query.data[1].length === 0 ? (
            <p>Nema aktivnosti.</p>
          ) : (
            <ul>
              {query.data[1].map((a) => (
                <li key={a.id}>
                  {a.type}: {a.outcome} —{" "}
                  {new Date(a.at).toLocaleDateString("sr-Latn-RS")}
                </li>
              ))}
            </ul>
          )}
          {user?.role === "SALES_REP" && query.data[0].at && (
            <form
              className="mt-4"
              onSubmit={async (e) => {
                e.preventDefault();
                if (guard.current) return;
                guard.current = true;
                setBusy(true);
                setFailure(null);
                try {
                  await riskApi.activity(customerId, {
                    type: "CONTACT",
                    notes: "",
                    outcome,
                  });
                  if (query.isCurrent()) {
                    setOutcome("");
                    query.reload();
                  }
                } catch (error) {
                  if (query.isCurrent())
                    setFailure({
                      context: query.context,
                      text:
                        error instanceof Error
                          ? error.message
                          : "Aktivnost nije sačuvana.",
                    });
                } finally {
                  guard.current = false;
                  if (query.isCurrent()) setBusy(false);
                }
              }}
            >
              <label>
                Ishod kontakta
                <input
                  required
                  maxLength={1000}
                  value={outcome}
                  onChange={(e) => setOutcome(e.target.value)}
                  className="my-2 block w-full rounded border border-dust-grey p-2"
                />
              </label>
              <button
                disabled={busy}
                className="rounded bg-yale-blue px-4 py-2 text-white"
              >
                {busy ? "Čuvanje..." : "Evidentiraj kontakt"}
              </button>
            </form>
          )}
        </>
      )}
      {failure?.context === query.context && <p role="alert">{failure.text}</p>}
    </section>
  );
}
