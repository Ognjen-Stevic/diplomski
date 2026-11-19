import { apiRequest } from "../../../app/api/http_client";
import type { Activity, ActivityInput, Assessment, RiskBatchItem, RiskPage, Summary } from "../types/risk_types";

const paged = (query: string) => {
  const parameters = new URLSearchParams(query);
  parameters.set("size", "20");
  return parameters.toString();
};

export const riskApi = {
  list: (query: string, signal: AbortSignal) =>
    apiRequest<RiskPage>(`/api/ai/customers/risks?${paged(query)}`, { signal }),
  summary: (query: string, signal: AbortSignal) =>
    apiRequest<Summary>(`/api/ai/summary?${query}`, { signal }),
  detail: (id: number, signal: AbortSignal) =>
    apiRequest<Assessment>(`/api/ai/customers/${id}/risk`, { signal }),
  batch: (ids: number[], signal: AbortSignal) =>
    apiRequest<RiskBatchItem[]>("/api/ai/customers/risks/batch", {
      method: "POST",
      body: JSON.stringify(ids),
      signal,
    }),
  activities: (id: number, signal: AbortSignal) =>
    apiRequest<Activity[]>(`/api/ai/customers/${id}/activities`, { signal }),
  activity: (
    id: number,
    payload: ActivityInput,
  ) =>
    apiRequest<void>(`/api/ai/customers/${id}/activities`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
};
