import { useEffect, useState, type FormEvent } from "react";

import { customerApi } from "../customer_api";
import type { SalesRepresentativeSummary } from "../types/customer.types";

export function CustomerSalesRepControl({
    currentSalesRep,
    onSalesRepChange,
}: {
    currentSalesRep: SalesRepresentativeSummary;
    onSalesRepChange: (salesRepId: number) => Promise<void>;
}) {
    const [salesRepresentatives, setSalesRepresentatives] = useState<SalesRepresentativeSummary[]>([
        currentSalesRep,
    ]);
    const [selectedSalesRepId, setSelectedSalesRepId] = useState(currentSalesRep.id);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        let active = true;
        customerApi.findTeamSalesRepresentatives()
            .then((result) => {
                if (active) {
                    setSalesRepresentatives(result.some((item) => item.id === currentSalesRep.id)
                        ? result
                        : [currentSalesRep, ...result]);
                }
            })
            .catch((requestError: unknown) => {
                if (active) {
                    setError(requestError instanceof Error
                        ? requestError.message
                        : "Komercijalisti nisu učitani.");
                }
            })
            .finally(() => {
                if (active) setLoading(false);
            });

        return () => {
            active = false;
        };
    }, [currentSalesRep]);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (selectedSalesRepId === currentSalesRep.id) return;
        setSaving(true);
        setError("");
        try {
            await onSalesRepChange(selectedSalesRepId);
        } catch (requestError) {
            setError(requestError instanceof Error
                ? requestError.message
                : "Komercijalista nije promenjen.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <section className="border-t border-dust-grey bg-white px-5 py-4 sm:px-7">
            <form onSubmit={submit}>
                <label htmlFor="customer-sales-rep" className="text-sm font-semibold text-yale-blue">
                    Dodeljeni komercijalista
                </label>
                <div className="mt-2 flex gap-3">
                    <select
                        id="customer-sales-rep"
                        value={selectedSalesRepId}
                        disabled={loading || saving}
                        onChange={(event) => setSelectedSalesRepId(Number(event.target.value))}
                        className="min-w-0 flex-1 rounded-xl border border-dust-grey bg-white px-3 py-2.5 text-sm text-graphite focus:border-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        {salesRepresentatives.map((salesRepresentative) => (
                            <option key={salesRepresentative.id} value={salesRepresentative.id}>
                                {salesRepresentative.firstName} {salesRepresentative.lastName}
                            </option>
                        ))}
                    </select>
                    <button
                        type="submit"
                        disabled={loading || saving || selectedSalesRepId === currentSalesRep.id}
                        className="rounded-xl bg-yale-blue px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-stormy-teal disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        {saving ? "Čuvanje..." : "Promeni"}
                    </button>
                </div>
                {loading && <p className="mt-2 text-sm text-graphite/60">Učitavanje komercijalista...</p>}
                {error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
            </form>
        </section>
    );
}
