export function StatCard({ value, label, }: { value: string; label: string; }) {
    return (
        <div className="rounded-2xl border border-dust-grey bg-white p-5 shadow-sm transition hover:border-stormy-teal hover:shadow-md focus:outline-none focus:ring-2 focus:ring-stormy-teal/30">
            <strong className="block text-2xl font-bold text-yale-blue">{value}</strong>
            <span className="mt-1 block text-sm text-graphite/65">{label}</span>
        </div>
    );
}