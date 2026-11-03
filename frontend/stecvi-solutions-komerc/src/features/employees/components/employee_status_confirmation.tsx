import { useDialog } from "../../../shared/hooks/use_dialog";
import { AlertTriangle } from "lucide-react";
import type { Employee } from "../types/employee.types";

export function EmployeeStatusConfirmation({
    employee,
    submitting,
    error,
    onConfirm,
    onCancel,
}: {
    employee: Employee;
    submitting: boolean;
    error?: string;
    onConfirm: () => void;
    onCancel: () => void;
}) {
    const dialog = useDialog(onCancel, submitting);
    const activating = !employee.active;

    return (
        <div ref={dialog} tabIndex={-1} className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/45 p-4" role="dialog" aria-modal="true" aria-labelledby="employee-status-title">
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-amber-50 text-amber-700">
                    <AlertTriangle className="h-6 w-6" />
                </span>
                <h2 id="employee-status-title" className="mt-4 text-xl font-bold text-yale-blue">
                    Da li ste sigurni?
                </h2>
                <p className="mt-2 text-graphite/65">
                    Da li želite da {activating ? "aktivirate" : "deaktivirate"} zaposlenog <strong className="text-graphite">{employee.firstName} {employee.lastName}</strong>?
                </p>
                {error && <p role="alert" className="mt-3 text-red-700">{error}</p>}
                <div className="mt-6 flex justify-end gap-3">
                    <button type="button" disabled={submitting} onClick={onCancel} className="rounded-xl border border-dust-grey px-5 py-2.5 font-bold text-graphite disabled:opacity-50">Ne</button>
                    <button type="button" disabled={submitting} onClick={onConfirm} className="rounded-xl bg-yale-blue px-5 py-2.5 font-bold text-white hover:bg-stormy-teal disabled:opacity-50">{submitting ? "Čuvanje..." : "Da"}</button>
                </div>
            </div>
        </div>
    );
}
