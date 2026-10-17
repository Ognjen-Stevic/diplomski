export function DetailRow({ label, value }: { label: string; value: string }) {
    return (
        <div className="grid gap-1 border-b border-dust-grey py-3 last:border-b-0 sm:grid-cols-[9rem_minmax(0,1fr)] sm:gap-4">
            <dt className="text-sm font-semibold text-graphite/60">{label}</dt>
            <dd className="break-words text-sm font-medium text-graphite">{value}</dd>
        </div>
    );
}