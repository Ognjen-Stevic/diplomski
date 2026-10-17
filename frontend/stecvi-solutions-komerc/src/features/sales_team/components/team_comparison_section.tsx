import { useState } from "react";

import { PeriodSelector } from "../../../shared/components/period_selector";
import { ReportChart } from "../../report/components/report_chart";
import { formatInteger, formatRsd } from "../../report/utils/sales_report.utils";
import { useTeamComparison } from "../hooks/use_team_comparison";
import type {
    ManagerComparisonMember,
    TeamComparisonMember,
} from "../types/sales_team.types";

type ComparisonMember = TeamComparisonMember | ManagerComparisonMember;

function PairedComparisonChart({
    title,
    description,
    members,
    firstLabel,
    secondLabel,
    firstValue,
    secondValue,
}: {
    title: string;
    description: string;
    members: ComparisonMember[];
    firstLabel: string;
    secondLabel: string;
    firstValue: (member: ComparisonMember) => number;
    secondValue: (member: ComparisonMember) => number;
}) {
    const maximum = Math.max(
        ...members.flatMap((member) => [firstValue(member), secondValue(member)]),
        1,
    );

    return (
        <section className="mt-6 rounded-2xl border border-dust-grey bg-white p-5 shadow-sm sm:p-6">
            <h2 className="text-lg font-bold text-yale-blue">{title}</h2>
            <p className="mt-0.5 text-sm text-graphite/55">{description}</p>
            <div className="mt-6 space-y-5" role="img" aria-label={description}>
                {members.map((member) => {
                    const first = firstValue(member);
                    const second = secondValue(member);
                    const memberId = "salesRepId" in member ? member.salesRepId : member.managerId;
                    return (
                        <div key={memberId}>
                            <p className="mb-2 text-sm font-semibold text-graphite">
                                {member.firstName} {member.lastName}
                            </p>
                            <div className="space-y-2.5">
                                <div>
                                    <div className="mb-1 flex items-center justify-between gap-3 text-xs">
                                        <span className="font-semibold text-graphite/65">{firstLabel}</span>
                                        <span className="font-bold tabular-nums text-yale-blue">{formatInteger(first)}</span>
                                    </div>
                                    <div className="h-2.5 overflow-hidden rounded-full bg-dust-grey/70">
                                        <div className="h-full rounded-full bg-yale-blue" style={{ width: `${Math.max((first / maximum) * 100, 2)}%` }} />
                                    </div>
                                </div>
                                <div>
                                    <div className="mb-1 flex items-center justify-between gap-3 text-xs">
                                        <span className="font-semibold text-graphite/65">{secondLabel}</span>
                                        <span className="font-bold tabular-nums text-stormy-teal">{formatInteger(second)}</span>
                                    </div>
                                    <div className="h-2.5 overflow-hidden rounded-full bg-dust-grey/70">
                                        <div className="h-full rounded-full bg-stormy-teal" style={{ width: `${Math.max((second / maximum) * 100, 2)}%` }} />
                                    </div>
                                </div>
                            </div>
                        </div>
                    );
                })}
            </div>
        </section>
    );
}

export function TeamComparisonSection({
    enabled,
    managerId = null,
    variant = "sales-reps",
}: {
    enabled: boolean;
    managerId?: number | null;
    variant?: "sales-reps" | "managers";
}) {
    const [selectedDate, setSelectedDate] = useState(() => {
        const now = new Date();
        return new Date(now.getFullYear(), now.getMonth(), 1);
    });
    const history = useTeamComparison({
        enabled,
        mode: variant,
        year: selectedDate.getFullYear(),
        month: selectedDate.getMonth() + 1,
        managerId,
    });
    const teamComparison = history.comparison && "salesRepresentatives" in history.comparison
        ? history.comparison
        : null;
    const managerComparison = history.comparison && "managers" in history.comparison
        ? history.comparison
        : null;
    const members: ComparisonMember[] = variant === "managers"
        ? managerComparison?.managers ?? []
        : teamComparison?.salesRepresentatives ?? [];
    const chartItems = (value: (member: ComparisonMember) => number) =>
        members.map((member) => ({
            label: `${member.firstName} ${member.lastName}`,
            value: value(member),
        }));

    return (
        <section className="mt-8">
            <div>
                <p className="text-xs font-bold uppercase tracking-[0.14em] text-stormy-teal">Poređenje učinka</p>
                <h2 className="mt-1 text-2xl font-bold text-yale-blue">
                    {variant === "managers" ? "Rezultati menadžera" : "Rezultati komercijalista"}
                </h2>
            </div>
            <PeriodSelector value={selectedDate} onChange={setSelectedDate} />

            {history.loading && (
                <p className="mt-8 text-center text-sm text-graphite/55">Učitavanje poređenja...</p>
            )}
            {!history.loading && history.error && (
                <div role="alert" className="mt-6 flex items-center justify-between gap-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                    <span>{history.error}</span>
                    <button type="button" onClick={history.retry} className="shrink-0 font-bold hover:underline">
                        Pokušaj ponovo
                    </button>
                </div>
            )}
            {!history.loading && !history.error && history.comparison && (
                <>
                    <div className="grid gap-5 xl:grid-cols-3 [&>section]:mt-6">
                        <PairedComparisonChart
                            title="Prodaja"
                            description={variant === "managers"
                                ? "Prodate flaše i baloni po timu menadžera."
                                : "Prodate flaše i baloni po komercijalisti."}
                            members={members}
                            firstLabel="Prodate flaše"
                            secondLabel="Prodati baloni"
                            firstValue={(member) => member.bottlesSold}
                            secondValue={(member) => member.balloonsSold}
                        />
                        <PairedComparisonChart
                            title="Gratis"
                            description={variant === "managers"
                                ? "Gratis flaše i baloni po timu menadžera."
                                : "Gratis flaše i baloni po komercijalisti."}
                            members={members}
                            firstLabel="Gratis flaše"
                            secondLabel="Gratis baloni"
                            firstValue={(member) => member.gratisBottles}
                            secondValue={(member) => member.gratisBalloons}
                        />
                        <ReportChart
                            title="Ostvarena stimulacija"
                            description={variant === "managers"
                                ? "Menadžerska stimulacija ostvarena po timu."
                                : "Lična stimulacija svakog komercijaliste."}
                            items={chartItems((member) => member.stimulation)}
                            formatValue={formatRsd}
                            orientation="horizontal"
                        />
                    </div>

                    {variant === "sales-reps" && teamComparison && (
                        <div className="mt-6 rounded-2xl bg-yale-blue p-6 text-white shadow-sm">
                            <p className="text-sm font-semibold text-white/70">Vaša stimulacija (svi članovi tima)</p>
                            <strong className="mt-2 block text-3xl font-bold tabular-nums">
                                {formatRsd(teamComparison.managerStimulation)}
                            </strong>
                            <p className="mt-2 text-sm text-white/65">
                                Obračunato prema ukupno prodatim količinama i menadžerskoj stimulaciji po artiklu.
                            </p>
                        </div>
                    )}
                </>
            )}
        </section>
    );
}
