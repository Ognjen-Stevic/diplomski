export type Level = "LOW" | "MEDIUM" | "HIGH" | "INSUFFICIENT_DATA";

export type Assessment = {
  level: Level;
  at: string | null;
  status: "ASSESSED" | "INSUFFICIENT_DATA" | "MODEL_UNAVAILABLE";
  factors: string[];
  recommendation: string;
  stale: boolean;
  features: Record<string, number> | null;
};

export type RiskListItem = {
  customerId: number;
  customerName: string;
  level: Level;
  at: string | null;
  recommendation: string;
  stale: boolean;
};

export type RiskBatchItem = { customerId: number; level: Level };

export type RiskPage = { content: RiskListItem[]; total: number; page: number; size: number };

export type Summary = {
  total: number;
  distribution: Partial<Record<Level, number>>;
  increased: number;
  highRiskPercentage: number | null;
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
};

export type ActivityInput = { type: string; notes: string; outcome: string };
