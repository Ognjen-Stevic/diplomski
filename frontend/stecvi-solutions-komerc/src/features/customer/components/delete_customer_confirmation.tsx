export function DeleteCustomerConfirmation({
    customerName,
    submitting,
    onConfirm,
    onCancel,
}: {
    customerName: string;
    submitting: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}) {
    return (
        <div
            className="fixed inset-0 z-[60] flex items-center justify-center bg-graphite/55 px-4"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="delete-customer-title"
            aria-describedby="delete-customer-description"
        >
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
                <h2 id="delete-customer-title" className="text-xl font-bold text-yale-blue">
                    Brisanje kupca
                </h2>
                <p id="delete-customer-description" className="mt-3 text-sm leading-6 text-graphite/70">
                    Da li ste sigurni da želite da obrišete kupca {customerName}?
                </p>
                <div className="mt-6 grid grid-cols-2 gap-3">
                    <button
                        type="button"
                        onClick={onCancel}
                        disabled={submitting}
                        className="rounded-xl border border-dust-grey px-4 py-2.5 text-sm font-semibold text-yale-blue transition hover:border-stormy-teal hover:bg-dust-grey/20 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        Ne
                    </button>
                    <button
                        type="button"
                        onClick={onConfirm}
                        disabled={submitting}
                        className="rounded-xl bg-red-700 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        {submitting ? "Brisanje..." : "Da"}
                    </button>
                </div>
            </div>
        </div>
    );
}
