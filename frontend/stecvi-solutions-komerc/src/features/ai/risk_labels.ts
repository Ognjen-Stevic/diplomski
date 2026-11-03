import type { Level } from "./risk_api";
export const featureLabels: Record<string, string> = {
  recencyDays: "Dana od poslednje porudžbine",
  intervalDays: "Prosečan razmak porudžbina (dani)",
  orders30: "Porudžbine — 30 dana",
  orders60: "Porudžbine — 60 dana",
  orders90: "Porudžbine — 90 dana",
  liters60: "Prodato litara — 60 dana",
  priorLiters60: "Prodato litara — prethodnih 60 dana",
  relationshipDays: "Trajanje odnosa (dani)",
};
export const riskLabels: Record<Level, string> = {
  LOW: "Nizak rizik",
  MEDIUM: "Srednji rizik",
  HIGH: "Visok rizik",
  INSUFFICIENT_DATA: "Nedovoljno podataka",
};
