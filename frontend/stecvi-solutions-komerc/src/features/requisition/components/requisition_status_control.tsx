import { useState, type FormEvent } from "react";

import { REQUISITION_STATUS_LABELS } from "../data/requisition.data";
import type { RequisitionStatus } from "../types/requisition.types";
import { RequisitionStatusConfirmation } from "./requisition_status_confirmation";

export function RequisitionStatusControl({
    allowedTransitions,
    onStatusChange,
}: {
    allowedTransitions: RequisitionStatus[];
    onStatusChange: (status: RequisitionStatus, note: string | null) => Promise<void>;
}) {
    const [status, setStatus] = useState<RequisitionStatus>(allowedTransitions[0]);
    const [note, setNote] = useState("");
    const [confirming, setConfirming] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");
    const requiresNote = status === "rejected" || status === "rejected_by_bookkeeper";
    const acceptsNote = requiresNote || status === "cancelled_by_bookkeeper" || status === "cancelled_by_manager";

    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setError("");
        if (requiresNote && !note.trim()) {
            setError("Napomena je obavezna kada se trebovanje odbija.");
            return;
        }
        setConfirming(true);
    };

    const confirm = async () => {
        setSaving(true);
        setError("");
        try {
            await onStatusChange(status, acceptsNote ? note.trim() || null : null);
            setConfirming(false);
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : "Status trebovanja nije promenjen.");
            setConfirming(false);
        } finally {
            setSaving(false);
        }
    };

    return (
        <footer className="border-t border-dust-grey bg-white px-5 py-4 sm:px-7">
            <form onSubmit={submit}>
                <label htmlFor="requisition-status" className="text-sm font-semibold text-yale-blue">Promeni status</label>
                <div className="mt-2 flex gap-3">
                    <select id="requisition-status" value={status} disabled={saving} onChange={(event) => { setStatus(event.target.value as RequisitionStatus); setNote(""); setError(""); }} className="min-w-0 flex-1 rounded-xl border border-dust-grey bg-white px-3 py-2.5 text-sm text-graphite focus:border-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 disabled:opacity-50">
                        {allowedTransitions.map((transition) => <option key={transition} value={transition}>{REQUISITION_STATUS_LABELS[transition]}</option>)}
                    </select>
                    <button type="submit" disabled={saving} className="rounded-xl bg-yale-blue px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-stormy-teal disabled:opacity-50">Promeni status</button>
                </div>
                {acceptsNote && (
                    <label className="mt-3 block text-sm font-semibold text-graphite">
                        Napomena {requiresNote && <span className="text-red-600">*</span>}
                        <textarea value={note} onChange={(event) => setNote(event.target.value)} rows={3} className="mt-2 w-full resize-y rounded-xl border border-dust-grey px-3 py-2.5 font-normal outline-none focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20" />
                    </label>
                )}
                {error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
            </form>
            {confirming && <RequisitionStatusConfirmation status={status} submitting={saving} onCancel={() => setConfirming(false)} onConfirm={() => void confirm()} />}
        </footer>
    );
}
