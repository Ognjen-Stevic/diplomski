import { useCallback } from "react";
import { useAuth } from "../../auth/hooks/useAuth";
import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { riskApi } from "../apis/risk_api";
export function useRiskBatch(ids: number[]) {
  const key = ids.join(",");
  const { user, hasPermission } = useAuth();
  const load = useCallback(
    (signal: AbortSignal) =>
      riskApi.batch(key ? key.split(",").map(Number) : [], signal),
    [key],
  );
  return useSessionRequest(
    `risk-batch:${key}`,
    load,
    Boolean(key) && user?.role !== "BOOKKEEPER" && hasPermission("customers.read"),
  );
}
