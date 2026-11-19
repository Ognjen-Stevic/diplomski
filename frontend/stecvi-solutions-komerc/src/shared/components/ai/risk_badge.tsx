import { riskLabels } from "../../../features/ai/constants/risk_labels";
import type { Level } from "../../../features/ai/types/risk_types";

export function RiskBadge({ level }: { level: Level }) {
  const colors =
    level === "HIGH"
      ? "bg-red-50 text-red-700"
      : level === "MEDIUM"
        ? "bg-amber-50 text-amber-800"
        : level === "LOW"
          ? "bg-green-50 text-green-800"
          : "bg-dust-grey/40 text-graphite";
  return (
    <span className={`inline-block rounded-full px-3 py-1 text-xs font-bold ${colors}`}>
      {riskLabels[level]}
    </span>
  );
}
