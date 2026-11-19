import { useId } from "react";
import { ChevronDown, SlidersHorizontal } from "lucide-react";
import { riskLabels } from "../../../features/ai/constants/risk_labels";

export type RiskFilterProps = {
  value: string;
  onChange: (value: string) => void;
  label?: string;
};

export function AiRiskFilter({ value, onChange, label = "Rizik" }: RiskFilterProps) {
  const id = useId();
  return (
    <div className="min-w-0">
      <label htmlFor={id} className="mb-2 flex items-center gap-2 text-sm font-semibold text-graphite/70">
        <SlidersHorizontal className="h-4 w-4 text-stormy-teal" aria-hidden="true" />
        {label}
      </label>
      <div className="relative">
        <select
          id={id}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="w-full appearance-none rounded-2xl border border-dust-grey bg-white py-4 pl-4 pr-11 text-graphite shadow-sm outline-none transition focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20"
        >
          <option value="">Svi kupci</option>
          {Object.entries(riskLabels).map(([key, text]) => (
            <option key={key} value={key}>{text}</option>
          ))}
        </select>
        <ChevronDown className="pointer-events-none absolute right-4 top-1/2 h-5 w-5 -translate-y-1/2 text-graphite/45" aria-hidden="true" />
      </div>
    </div>
  );
}
