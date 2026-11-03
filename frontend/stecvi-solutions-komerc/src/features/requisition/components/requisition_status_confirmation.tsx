import type { RequisitionStatus } from "../types/requisition.types";
import { REQUISITION_STATUS_LABELS } from "../data/requisition.data";

export function RequisitionStatusConfirmation({
    status,
    submitting,
    error,
    onConfirm,
    onCancel,
}: {
    status: RequisitionStatus;
    submitting: boolean;
    error?: string;
    onConfirm: () => void;
    onCancel: () => void;
}) {
    return (
        <div className="fixed inset-0 z-[70] grid place-items-center bg-graphite/55 p-4" role="alertdialog" aria-modal="true" aria-labelledby="status-confirmation-title">
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
                <h3 id="status-confirmation-title" className="text-xl font-bold text-yale-blue">Potvrda promene statusa</h3>
                <p className="mt-3 text-sm text-graphite/70">
                    Da li ste sigurni da želite da promenite status u <strong>{REQUISITION_STATUS_LABELS[status]}</strong>?
                </p>
                {error && <p role="alert" className="mt-3 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
                <div className="mt-6 flex justify-end gap-3">
                    <button type="button" disabled={submitting} onClick={onCancel} className="rounded-xl border border-dust-grey px-4 py-2.5 font-semibold text-graphite transition hover:bg-dust-grey/30 disabled:opacity-50">Ne</button>
                    <button type="button" disabled={submitting} onClick={onConfirm} className="rounded-xl bg-yale-blue px-4 py-2.5 font-semibold text-white transition hover:bg-stormy-teal disabled:opacity-50">{submitting ? "Čuvanje..." : "Da"}</button>
                </div>
            </div>
        </div>
    );
}
