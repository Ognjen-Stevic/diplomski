import { useState, type FormEvent } from "react";

import type { CustomerStatus } from "../types/customer.types";
import { DeleteCustomerConfirmation } from "./delete_customer_confirmation";

export function CustomerStatusControl({
    customerName,
    currentStatus,
    onStatusChange,
}: {
    customerName: string;
    currentStatus: CustomerStatus;
    onStatusChange: (status: CustomerStatus) => Promise<void>;
}) {
    const [status, setStatus] = useState<CustomerStatus>(currentStatus);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");
    const [confirmingDeletion, setConfirmingDeletion] = useState(false);

    const changeStatus = async (nextStatus: CustomerStatus) => {
        setSaving(true);
        setError("");
        try {
            await onStatusChange(nextStatus);
            setConfirmingDeletion(false);
        } catch (requestError) {
            setError(requestError instanceof Error
                ? requestError.message
                : "Status kupca nije promenjen.");
        } finally {
            setSaving(false);
        }
    };

    const submitStatus = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (status === currentStatus) return;
        if (status === "deleted") {
            setConfirmingDeletion(true);
            return;
        }
        void changeStatus(status);
    };

    return (
        <footer className="border-t border-dust-grey bg-white px-5 py-4 sm:px-7">
            <form onSubmit={submitStatus}>
                <label htmlFor="customer-status" className="text-sm font-semibold text-yale-blue">
                    Status kupca
                </label>
                <div className="mt-2 flex gap-3">
                    <select
                        id="customer-status"
                        value={status}
                        disabled={saving}
                        onChange={(event) => setStatus(event.target.value as CustomerStatus)}
                        className="min-w-0 flex-1 rounded-xl border border-dust-grey bg-white px-3 py-2.5 text-sm text-graphite focus:border-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        <option value="draft" disabled>Čeka odobrenje</option>
                        <option value="approved">Odobren</option>
                        <option value="deactivated">Deaktiviran</option>
                        <option value="deleted">Obrisan</option>
                    </select>
                    <button
                        type="submit"
                        disabled={saving || status === currentStatus}
                        className="rounded-xl bg-yale-blue px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-stormy-teal disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        {saving ? "Čuvanje..." : "Promeni status"}
                    </button>
                </div>
                {error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
            </form>

            {confirmingDeletion && (
                <DeleteCustomerConfirmation
                    customerName={customerName}
                    submitting={saving}
                    onConfirm={() => void changeStatus("deleted")}
                    onCancel={() => setConfirmingDeletion(false)}
                />
            )}
        </footer>
    );
}
