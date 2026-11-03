import { useCallback } from "react";
import { apiRequest } from "../../app/api/http_client";
import { useSessionRequest } from "../../shared/hooks/use_session_request";
import { riskLabels } from "./risk_labels";
import type { Level } from "./risk_api";
type Trend = {
  captured_date: string;
  level: Level;
  customers: number;
  model_version: string | null;
};
export function RiskTrends({ scope }: { scope: string }) {
  const load = useCallback(
    (signal: AbortSignal) =>
      apiRequest<Trend[]>(`/api/ai/risk-trends?${scope}`, { signal }),
    [scope],
  );
  const query = useSessionRequest(`risk-trends:${scope}`, load);
  return (
    <details className="mt-4">
      <summary>Trend rizika — poslednjih 30 dana</summary>
      {query.loading && <p>Učitavanje trenda...</p>}
      {query.error && (
        <p role="alert">
          {query.error} <button onClick={query.reload}>Pokušaj ponovo</button>
        </p>
      )}
      {query.data?.length === 0 && <p>Nema istorijskih procena.</p>}
      {query.data && query.data.length > 0 && (
        <div className="overflow-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr>
                <th>Datum</th>
                <th>Rizik</th>
                <th>Kupci</th>
                <th>Model</th>
              </tr>
            </thead>
            <tbody>
              {query.data.map((row, index) => (
                <tr key={index}>
                  <td>{row.captured_date}</td>
                  <td>{riskLabels[row.level]}</td>
                  <td>{row.customers}</td>
                  <td>{row.model_version ?? "Nema aktivnog modela"}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p>
            Poslednja procena kupca po danu. Promena verzije modela može uticati
            na poređenje kategorija.
          </p>
        </div>
      )}
    </details>
  );
}
