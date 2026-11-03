import { useId, useState } from "react";
import { ChevronDown, SlidersHorizontal } from "lucide-react";

import { SessionScope } from "../../../shared/components/session_scope";
import { CustomerSearch } from "../../customer/components/customer_search";
import { riskLabels } from "../risk_labels";

type AiCustomerFiltersProps = {
    search: string;
    risk: string;
    onSearch: (value: string) => void;
    onRiskChange: (value: string) => void;
};

export function AiRiskFilter({
    value,
    onChange,
    label = "Rizik",
}: {
    value: string;
    onChange: (value: string) => void;
    label?: string;
}) {
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

export function AiCustomerFilters(props: AiCustomerFiltersProps) {
    return <SessionScope><CustomerFiltersForm {...props} /></SessionScope>;
}

function CustomerFiltersForm({ search, risk, onSearch, onRiskChange }: AiCustomerFiltersProps) {
    // Follow URL navigation without replacing the user's unsent search draft on other filter changes.
    const [draft, setDraft] = useState({ search, value: search });
    const value = draft.search === search ? draft.value : search;

    return (
        <section aria-label="Pretraga i filteri kupaca" className="my-6 rounded-2xl border border-dust-grey bg-white p-4 shadow-sm sm:p-5">
            <div className="grid items-end gap-4 lg:grid-cols-[minmax(0,1fr)_16rem]">
                <div className="min-w-0">
                    <p className="mb-2 text-sm font-semibold text-graphite/70">Pretraga kupaca</p>
                    <CustomerSearch
                        value={value}
                        onChange={(next) => setDraft({ search, value: next })}
                        onSubmit={(event) => {
                            event.preventDefault();
                            onSearch(value.trim());
                        }}
                    />
                </div>
                <AiRiskFilter value={risk} onChange={onRiskChange} />
            </div>
        </section>
    );
}
