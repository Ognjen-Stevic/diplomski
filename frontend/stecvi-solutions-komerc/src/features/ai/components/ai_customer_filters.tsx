import { useState } from "react";

import { SessionScope } from "../../../shared/components/session_scope";
import { CustomerSearch } from "../../../shared/components/customer_search";
import { AiRiskFilter } from "../../../shared/components/ai/risk_filter";

type AiCustomerFiltersProps = {
    search: string;
    risk: string;
    onSearch: (value: string) => void;
    onRiskChange: (value: string) => void;
};

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
