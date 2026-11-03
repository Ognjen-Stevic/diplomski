type ChartItem = {
    label: string;
    value: number;
};

type ReportChartProps = {
    title: string;
    description: string;
    items: ChartItem[];
    formatValue: (value: number) => string;
    orientation?: "vertical" | "horizontal";
};

export function ReportChart({ title, description, items, formatValue, orientation = "vertical" }: ReportChartProps) {
    const maximum = items.reduce((maximum, item) =>
        Number.isFinite(item.value) ? Math.max(maximum, item.value) : maximum, 0);
    const percentage = (value: number, scale: number) =>
        maximum > 0 && Number.isFinite(value) && value > 0 ? (value / maximum) * scale : 0;

    return (
        <section className="mt-6 rounded-2xl border border-dust-grey bg-white p-5 shadow-sm sm:p-6">
            <h2 className="text-lg font-bold text-yale-blue">{title}</h2>
            <p className="mt-0.5 text-sm text-graphite/55">{description}</p>

            {orientation === "vertical" ? (
                <div className="mt-6 overflow-x-auto">
                    <div className="flex h-72 min-w-[680px] items-end gap-3 border-b border-l border-dust-grey px-4 pt-8" role="img" aria-label={description}>
                        {items.map((item) => (
                            <div key={item.label} className="flex h-full min-w-12 flex-1 flex-col items-center justify-end gap-2">
                                <span className="text-xs font-semibold tabular-nums text-graphite/60">{formatValue(item.value)}</span>
                                <div className="w-full max-w-14 rounded-t-lg bg-yale-blue transition hover:bg-stormy-teal" style={{ height: `${percentage(item.value, 82)}%` }} title={`${item.label}: ${formatValue(item.value)}`} />
                                <span className="pb-2 text-xs font-semibold text-graphite/60">{item.label}</span>
                            </div>
                        ))}
                    </div>
                </div>
            ) : (
                <div className="mt-6 space-y-4" role="img" aria-label={description}>
                    {items.map((item) => (
                        <div key={item.label}>
                            <div className="mb-1.5 flex items-center justify-between gap-4 text-sm">
                                <span className="font-semibold text-graphite">{item.label}</span>
                                <span className="font-bold tabular-nums text-yale-blue">{formatValue(item.value)}</span>
                            </div>
                            <div className="h-3 overflow-hidden rounded-full bg-dust-grey/70">
                                <div className="h-full rounded-full bg-stormy-teal" style={{ width: `${percentage(item.value, 100)}%` }} />
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </section>
    );
}
