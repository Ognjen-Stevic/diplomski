import { Search } from "lucide-react";
import { useMemo, useState } from "react";

import type { TeamSummaryMember } from "../types/sales_team.types";

type TeamMembersOverviewProps = {
    members: TeamSummaryMember[];
    loading: boolean;
    error: string;
    onRetry: () => void;
    onAction: (memberId: number) => void;
    onViewReport?: (memberId: number) => void;
    variant?: "team" | "managers";
};

export function TeamMembersOverview({
    members,
    loading,
    error,
    onRetry,
    onAction,
    onViewReport,
    variant = "team",
}: TeamMembersOverviewProps) {
    const [search, setSearch] = useState("");
    const showingManagers = variant === "managers";
    const filteredMembers = useMemo(() => {
        const normalizedSearch = search.trim().toLocaleLowerCase("sr-Latn-RS");
        if (!normalizedSearch) return members;
        return members.filter((member) => `${member.firstName} ${member.lastName}`
            .toLocaleLowerCase("sr-Latn-RS")
            .includes(normalizedSearch));
    }, [members, search]);

    return (
        <section className="mt-7 overflow-hidden rounded-2xl border border-dust-grey bg-white shadow-sm">
            <div className="flex flex-col gap-4 border-b border-dust-grey px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
                <div>
                    <h2 className="text-xl font-bold text-yale-blue">
                        {showingManagers ? "Menadžeri" : "Članovi tima"}
                    </h2>
                    <p className="mt-1 text-sm text-graphite/55">
                        {showingManagers
                            ? "Pregled menadžera i zbirnih rezultata njihovih timova."
                            : "Pregled komercijalista, kupaca i mesečnih trebovanja."}
                    </p>
                </div>
                <label className="relative block w-full sm:max-w-xs">
                    <span className="sr-only">
                        {showingManagers ? "Pretraži menadžere" : "Pretraži komercijaliste"}
                    </span>
                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-graphite/45" />
                    <input
                        type="search"
                        value={search}
                        onChange={(event) => setSearch(event.target.value)}
                        placeholder="Pretraži po imenu"
                        className="w-full rounded-xl border border-dust-grey bg-white py-2.5 pl-10 pr-3 text-sm text-graphite outline-none focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20"
                    />
                </label>
            </div>

            {loading && (
                <p className="px-6 py-10 text-center text-sm text-graphite/55">
                    Učitavanje članova tima...
                </p>
            )}
            {!loading && error && (
                <div role="alert" className="m-5 flex items-center justify-between gap-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                    <span>{error}</span>
                    <button type="button" onClick={onRetry} className="shrink-0 font-bold hover:underline">
                        Pokušaj ponovo
                    </button>
                </div>
            )}
            {!loading && !error && (
                <div className="overflow-x-auto">
                    <table className="w-full min-w-[980px] border-collapse text-left">
                        <thead>
                            <tr className="bg-yale-blue/[0.04] text-xs uppercase tracking-wider text-graphite/55">
                                <th className="px-6 py-3 font-bold">Ime i prezime</th>
                                <th className="px-5 py-3 font-bold">Email</th>
                                <th className="px-5 py-3 font-bold">Telefon</th>
                                {showingManagers && <th className="px-5 py-3 text-right font-bold">Članovi tima</th>}
                                <th className="px-5 py-3 text-right font-bold">Aktivni kupci</th>
                                <th className="px-5 py-3 text-right font-bold">
                                    {showingManagers ? "Trebovanja tima ovog meseca" : "Trebovanja ovog meseca"}
                                </th>
                                <th className="px-5 py-3 font-bold">Status</th>
                                <th className="px-6 py-3 text-right font-bold">
                                    {showingManagers ? "Akcije" : "Akcija"}
                                </th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-dust-grey/75">
                            {filteredMembers.length === 0 && (
                                <tr>
                                    <td colSpan={showingManagers ? 8 : 7} className="px-6 py-10 text-center text-sm text-graphite/55">
                                        {showingManagers ? "Nema pronađenih menadžera." : "Nema pronađenih članova tima."}
                                    </td>
                                </tr>
                            )}
                            {filteredMembers.map((member) => {
                                const memberId = "managerId" in member
                                    ? member.managerId
                                    : member.salesRepId;
                                return (
                                    <tr key={memberId} className="transition hover:bg-yale-blue/[0.025]">
                                        <th scope="row" className="px-6 py-4 font-semibold text-graphite">
                                            {member.firstName} {member.lastName}
                                        </th>
                                        <td className="px-5 py-4 text-sm text-graphite/65">{member.email}</td>
                                        <td className="px-5 py-4 text-sm text-graphite/65">
                                            {member.phone ? (
                                                <a
                                                    href={`tel:${member.phone.replace(/[^\d+]/g, "")}`}
                                                    aria-label={`Pozovi ${member.firstName} ${member.lastName} na broj ${member.phone}`}
                                                    className="font-semibold text-stormy-teal transition hover:text-yale-blue hover:underline focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                                                >
                                                    {member.phone}
                                                </a>
                                            ) : "—"}
                                        </td>
                                        {showingManagers && "teamMemberCount" in member && (
                                            <td className="px-5 py-4 text-right font-bold tabular-nums text-yale-blue">
                                                {member.teamMemberCount}
                                            </td>
                                        )}
                                        <td className="px-5 py-4 text-right font-bold tabular-nums text-yale-blue">
                                            {member.activeCustomers}
                                        </td>
                                        <td className="px-5 py-4 text-right font-bold tabular-nums text-yale-blue">
                                            {member.currentMonthRequisitions}
                                        </td>
                                        <td className="px-5 py-4">
                                            <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${member.active ? "bg-emerald-50 text-emerald-800 ring-emerald-200" : "bg-slate-100 text-slate-600 ring-slate-200"}`}>
                                                {member.active ? "Aktivan" : "Neaktivan"}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <div className="flex justify-end gap-2">
                                                <button
                                                    type="button"
                                                    onClick={() => onAction(memberId)}
                                                    className="rounded-xl bg-yale-blue px-4 py-2 text-sm font-bold text-white transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                                                >
                                                    {showingManagers ? "Vidi tim" : "Vidi izveštaj"}
                                                </button>
                                                {showingManagers && onViewReport && (
                                                    <button
                                                        type="button"
                                                        onClick={() => onViewReport(memberId)}
                                                        className="rounded-xl border border-yale-blue px-4 py-2 text-sm font-bold text-yale-blue transition hover:border-stormy-teal hover:text-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                                                    >
                                                        Vidi izveštaj
                                                    </button>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </section>
    );
}
