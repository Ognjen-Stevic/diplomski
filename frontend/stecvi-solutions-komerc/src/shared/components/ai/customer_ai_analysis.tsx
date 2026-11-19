import { useCallback, useRef, useState } from "react";
import { SessionScope } from "../session_scope";
import { useSessionRequest } from "../../hooks/use_session_request";
import { useAuth } from "../../../features/auth/hooks/useAuth";
import { featureLabels } from "../../../features/ai/constants/risk_labels";
import { riskApi } from "../../../features/ai/apis/risk_api";
import { RiskBadge } from "./risk_badge";

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
      Promise.all([riskApi.detail(customerId, signal), riskApi.activities(customerId, signal)]),
    [customerId],
  );
  const query = useSessionRequest(
    `customer-ai:${customerId}`,
    load,
    Boolean(user && user.role !== "BOOKKEEPER" && hasPermission("customers.read")),
  );
  const guard = useRef(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<{ context: object; text: string } | null>(null);
  const [outcome, setOutcome] = useState("");
  if (user?.role === "BOOKKEEPER" || !hasPermission("customers.read")) return null;

  const recordContact = async (event: React.FormEvent) => {
    event.preventDefault();
    if (guard.current) return;
    guard.current = true;
    setBusy(true);
    setFailure(null);
    try {
      await riskApi.activity(customerId, { type: "CONTACT", notes: "", outcome });
      if (query.isCurrent()) {
        setOutcome("");
        query.reload();
      }
    } catch (error) {
      if (query.isCurrent())
        setFailure({
          context: query.context,
          text: error instanceof Error ? error.message : "Aktivnost nije sačuvana.",
        });
    } finally {
      guard.current = false;
      if (query.isCurrent()) setBusy(false);
    }
  };

  const assessment = query.data?.[0];
  const activities = query.data?.[1];
  return (
    <section className="my-5 rounded-xl border border-dust-grey p-4">
      <h3 className="font-bold text-yale-blue">AI analiza</h3>
      {query.loading && <p role="status">Učitavanje analize...</p>}
      {query.error && <p role="alert">{query.error} <button onClick={query.reload}>Pokušaj ponovo</button></p>}
      {assessment && activities && (
        <>
          <RiskBadge level={assessment.level} />
          {assessment.status === "MODEL_UNAVAILABLE" && (
            <p role="alert" className="my-3 rounded-xl bg-amber-50 px-4 py-3 text-amber-900">
              AI model trenutno nije dostupan. Podaci kupca se i dalje prikupljaju, a procena će
              biti osvežena kada odgovarajući model bude dostupan.
            </p>
          )}
          <p>
            Procena:{" "}
            {assessment.at
              ? new Date(assessment.at).toLocaleString("sr-Latn-RS")
              : "Još nije izvršena"}
          </p>
          {assessment.stale && <p>Procena zahteva osvežavanje.</p>}
          {assessment.features && (
            <div className="my-4 overflow-hidden rounded-lg border border-dust-grey">
              <table className="w-full text-left text-sm">
                <caption className="sr-only">Podaci korišćeni za AI procenu</caption>
                <thead className="bg-slate-50 text-graphite/70">
                  <tr><th className="px-3 py-2">Pokazatelj</th><th className="px-3 py-2 text-right">Vrednost</th></tr>
                </thead>
                <tbody>
                  {Object.entries(assessment.features).map(([name, value]) => (
                    <tr key={name} className="border-t border-dust-grey">
                      <td className="px-3 py-2">{featureLabels[name] ?? name}</td>
                      <td className="px-3 py-2 text-right font-semibold tabular-nums">{value.toFixed(1)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {assessment.factors.length > 0 && <h4 className="font-bold">Putanja Decision Tree modela</h4>}
          <ul>
            {assessment.factors.map((factor, index) => (
              <li key={index}>
                {Object.entries(featureLabels).reduce(
                  (text, [key, label]) => text.replace(key, label),
                  factor,
                )}
              </li>
            ))}
          </ul>
          <p>{assessment.recommendation}</p>
          <h4 className="mt-4 font-bold">Evidentirane aktivnosti</h4>
          {activities.length === 0 ? <p>Nema aktivnosti.</p> : (
            <ul>
              {activities.map((activity) => (
                <li key={activity.id}>
                  {activity.type}: {activity.outcome} — {new Date(activity.at).toLocaleDateString("sr-Latn-RS")}
                </li>
              ))}
            </ul>
          )}
          {user?.role === "SALES_REP" && assessment.at && (
            <form className="mt-4" onSubmit={recordContact}>
              <label>
                Ishod kontakta
                <input
                  required
                  maxLength={1000}
                  value={outcome}
                  onChange={(event) => setOutcome(event.target.value)}
                  className="my-2 block w-full rounded border border-dust-grey p-2"
                />
              </label>
              <button disabled={busy} className="rounded bg-yale-blue px-4 py-2 text-white">
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
