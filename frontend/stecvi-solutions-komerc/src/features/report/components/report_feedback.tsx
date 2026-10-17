type ReportFeedbackProps = {
    loading: boolean;
    error: string;
    onRetry: () => void;
};

export function ReportFeedback({ loading, error, onRetry }: ReportFeedbackProps) {
    if (loading) {
        return <p className="mt-8 text-center text-sm text-graphite/60">Učitavanje izveštaja...</p>;
    }
    if (!error) return null;

    return (
        <div role="alert" className="mt-6 flex flex-col gap-3 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700 sm:flex-row sm:items-center sm:justify-between">
            <span>{error}</span>
            <button
                type="button"
                onClick={onRetry}
                className="self-start rounded-lg border border-red-200 bg-white px-3 py-1.5 font-semibold transition hover:bg-red-100 sm:self-auto"
            >
                Pokušaj ponovo
            </button>
        </div>
    );
}
