type PeriodSelectorProps = {
    value: Date;
    onChange: (value: Date) => void;
    mode?: "month" | "year";
};

const MONTHS = Array.from({ length: 12 }, (_, month) => ({
    value: month,
    label: new Intl.DateTimeFormat("sr-Latn-RS", { month: "long" }).format(new Date(2020, month, 1)),
}));

export function PeriodSelector({ value, onChange, mode = "month" }: PeriodSelectorProps) {
    const currentYear = new Date().getFullYear();
    const firstYear = Math.min(value.getFullYear(), currentYear - 10);
    const lastYear = Math.max(value.getFullYear(), currentYear);
    const years = Array.from({ length: lastYear - firstYear + 1 }, (_, index) => lastYear - index);

    return (
        <section className="mt-7 rounded-2xl border border-dust-grey bg-white p-4 shadow-sm sm:p-5">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                <div>
                    <p className="text-xs font-bold uppercase tracking-[0.14em] text-graphite/50">Period prikaza</p>
                    <p className="mt-1 text-sm text-graphite/60">
                        {mode === "year"
                            ? "Izaberite godinu za prikaz zbirnih rezultata."
                            : "Izaberite mesec i godinu za istoriju i zbirne količine."}
                    </p>
                </div>
                <div className={`grid gap-3 ${mode === "month" ? "grid-cols-2" : "grid-cols-1"}`}>
                    {mode === "month" && (
                        <label className="text-sm font-semibold text-graphite">Mesec
                            <select
                                value={value.getMonth()}
                                onChange={(event) => onChange(new Date(value.getFullYear(), Number(event.target.value), 1))}
                                className="mt-1.5 w-full min-w-36 rounded-xl border border-dust-grey bg-white px-4 py-2.5 capitalize text-yale-blue outline-none focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20"
                            >
                                {MONTHS.map((month) => <option key={month.value} value={month.value}>{month.label}</option>)}
                            </select>
                        </label>
                    )}
                    <label className="text-sm font-semibold text-graphite">Godina
                        <select
                            value={value.getFullYear()}
                            onChange={(event) => onChange(new Date(Number(event.target.value), value.getMonth(), 1))}
                            className="mt-1.5 w-full min-w-28 rounded-xl border border-dust-grey bg-white px-4 py-2.5 text-yale-blue outline-none focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20"
                        >
                            {years.map((year) => <option key={year} value={year}>{year}</option>)}
                        </select>
                    </label>
                </div>
            </div>
        </section>
    );
}
