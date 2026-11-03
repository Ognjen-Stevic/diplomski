import { apiRequest } from "../../app/api/http_client";
export type Level = "LOW" | "MEDIUM" | "HIGH" | "INSUFFICIENT_DATA";
export type Assessment = {
  customerId: number;
  customerName: string;
  salesRepId: number;
  managerId: number | null;
  level: Level;
  status: string;
  at: string | null;
  modelVersion: string | null;
  factors: string[];
  recommendation: string;
  stale: boolean;
  previousLevel: string | null;
  features: Record<string, number> | null;
};
export type RiskPage = {
  content: Assessment[];
  total: number;
  page: number;
  size: number;
};
export type Summary = {
  total: number;
  distribution: Partial<Record<Level, number>>;
  increased: number;
  highRiskPercentage: number | null;
  historicalHighRiskLiters: number;
  teams: {
    manager_id: number | null;
    manager_name: string;
    customers: number;
    high: number;
  }[];
};
export type Activity = {
  id: number;
  type: string;
  at: string;
  outcome: string;
  notes: string | null;
};
export const riskApi = {
  list: (query: string, signal: AbortSignal) =>
    apiRequest<RiskPage>(`/api/ai/customers/risks?${query}`, { signal }),
  summary: (query: string, signal: AbortSignal) =>
    apiRequest<Summary>(`/api/ai/summary?${query}`, { signal }),
  detail: (id: number, signal: AbortSignal) =>
    apiRequest<Assessment>(`/api/ai/customers/${id}/risk`, { signal }),
  batch: (ids: number[], signal: AbortSignal) =>
    apiRequest<Assessment[]>("/api/ai/customers/risks/batch", {
      method: "POST",
      body: JSON.stringify(ids),
      signal,
    }),
  activities: (id: number, signal: AbortSignal) =>
    apiRequest<Activity[]>(`/api/ai/customers/${id}/activities`, { signal }),
  activity: (
    id: number,
    payload: { type: string; notes: string; outcome: string },
  ) =>
    apiRequest<void>(`/api/ai/customers/${id}/activities`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
};
