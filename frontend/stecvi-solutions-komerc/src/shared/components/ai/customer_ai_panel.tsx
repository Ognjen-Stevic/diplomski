import { useEffect } from "react";
import { X } from "lucide-react";
import { CustomerAiAnalysis } from "./customer_ai_analysis";

type CustomerAiPanelProps = {
  customerId: number;
  customerName?: string;
  onClose: () => void;
};

export function CustomerAiPanel({ customerId, customerName, onClose }: CustomerAiPanelProps) {
  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex justify-end" role="dialog" aria-modal="true" aria-label="AI analiza kupca">
      <button className="absolute inset-0 bg-black/35" onClick={onClose} aria-label="Zatvori AI analizu" />
      <aside className="relative h-full w-full max-w-2xl overflow-y-auto bg-white p-5 shadow-2xl sm:p-7">
        <div className="flex items-start justify-between gap-4 border-b border-dust-grey pb-4">
          <div>
            <p className="text-sm font-semibold text-stormy-teal">AI analiza kupca</p>
            {customerName && <h2 className="text-2xl font-bold text-yale-blue">{customerName}</h2>}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-2 text-graphite transition hover:bg-dust-grey/50"
            aria-label="Zatvori panel"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
        <CustomerAiAnalysis customerId={customerId} />
      </aside>
    </div>
  );
}
