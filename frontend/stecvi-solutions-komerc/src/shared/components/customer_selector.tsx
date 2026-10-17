import { Building2, Search, X } from "lucide-react";

import type { Customer } from "../../features/customer/types/customer.types";

type CustomerSelectorProps = {
    selectedCustomer: Customer | null;
    matchingCustomers: Customer[];
    search: string;
    isPreselected: boolean;
    submitted: boolean;
    onSearchChange: (value: string) => void;
    onSelect: (customer: Customer) => void;
    onClear: () => void;
};

export function CustomerSelector({
    selectedCustomer,
    matchingCustomers,
    search,
    isPreselected,
    submitted,
    onSearchChange,
    onSelect,
    onClear,
}: CustomerSelectorProps) {
    const showError = submitted && !selectedCustomer;

    return (
        <section className="rounded-2xl border border-dust-grey bg-white p-5 shadow-sm sm:p-6">
            <div className="flex items-start gap-3">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-yale-blue/10 text-yale-blue">
                    <Building2 className="h-5 w-5" />
                </span>
                <div className="min-w-0 flex-1">
                    <h2 className="text-base font-bold text-yale-blue">
                        Kupac <span className="text-red-600">*</span>
                    </h2>
                    <p className="mt-0.5 text-sm text-graphite/55">
                        {isPreselected
                            ? "Kupac je preuzet iz prethodnog koraka."
                            : "Pronađite kupca po nazivu objekta, kompaniji, PIB-u ili adresi."}
                    </p>
                </div>
            </div>

            {selectedCustomer ? (
                <div className="mt-4 flex items-center justify-between gap-4 rounded-xl border border-stormy-teal/30 bg-stormy-teal/[0.06] px-4 py-3">
                    <div className="min-w-0">
                        <p className="truncate font-bold text-graphite">{selectedCustomer.objectName}</p>
                        <p className="mt-0.5 truncate text-sm text-graphite/60">
                            {selectedCustomer.companyName} · {selectedCustomer.address}
                        </p>
                    </div>
                    {!isPreselected && (
                        <button
                            type="button"
                            onClick={onClear}
                            className="inline-flex shrink-0 items-center gap-1.5 rounded-lg px-2.5 py-2 text-sm font-semibold text-yale-blue transition hover:bg-yale-blue/10 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                        >
                            <X className="h-4 w-4" />
                            Promeni
                        </button>
                    )}
                </div>
            ) : (
                <div className="relative mt-4">
                    <Search className="pointer-events-none absolute left-3.5 top-3.5 h-5 w-5 text-graphite/40" />
                    <input
                        type="search"
                        value={search}
                        onChange={(event) => onSearchChange(event.target.value)}
                        autoComplete="off"
                        aria-label="Pretraži kupce"
                        aria-invalid={showError}
                        placeholder="Pretražite kupce..."
                        className={`w-full rounded-xl border bg-white py-3 pl-11 pr-4 text-graphite outline-none transition placeholder:text-graphite/40 focus:ring-2 ${
                            showError
                                ? "border-red-500 focus:border-red-500 focus:ring-red-100"
                                : "border-dust-grey focus:border-stormy-teal focus:ring-stormy-teal/20"
                        }`}
                    />
                    {search.trim() && (
                        <div className="mt-2 overflow-hidden rounded-xl border border-dust-grey bg-white shadow-lg">
                            {matchingCustomers.length > 0 ? (
                                <ul aria-label="Rezultati pretrage kupaca" className="divide-y divide-dust-grey/70">
                                    {matchingCustomers.map((customer) => (
                                        <li key={customer.id}>
                                            <button
                                                type="button"
                                                onClick={() => onSelect(customer)}
                                                className="w-full px-4 py-3 text-left transition hover:bg-yale-blue/[0.05] focus:bg-yale-blue/[0.05] focus:outline-none"
                                            >
                                                <span className="block font-semibold text-graphite">{customer.objectName}</span>
                                                <span className="mt-0.5 block text-sm text-graphite/55">
                                                    {customer.companyName} · {customer.address}
                                                </span>
                                            </button>
                                        </li>
                                    ))}
                                </ul>
                            ) : (
                                <p className="px-4 py-4 text-sm text-graphite/60">
                                    Nema kupaca koji odgovaraju pretrazi.
                                </p>
                            )}
                        </div>
                    )}

                    {showError && (
                        <p role="alert" className="mt-2 text-sm font-medium text-red-700">
                            Pronađite i izaberite kupca.
                        </p>
                    )}
                </div>
            )}
        </section>
    );
}
