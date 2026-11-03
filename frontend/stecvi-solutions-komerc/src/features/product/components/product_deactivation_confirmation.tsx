import { useDialog } from "../../../shared/hooks/use_dialog";
import { AlertTriangle } from "lucide-react";
import type { ManagedProduct } from "../types/product_management.types";

export function ProductDeactivationConfirmation({
    product,
    submitting,
    error,
    onCancel,
    onConfirm,
}: {
    product: ManagedProduct;
    submitting: boolean;
    error?: string;
    onCancel: () => void;
    onConfirm: () => void;
}) {
    const dialog = useDialog(onCancel, submitting);
    return (
        <div ref={dialog} tabIndex={-1} className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/45 p-4" role="dialog" aria-modal="true" aria-labelledby="deactivate-product-title">
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-amber-50 text-amber-700"><AlertTriangle className="h-6 w-6" /></span>
                <h2 id="deactivate-product-title" className="mt-4 text-xl font-bold text-yale-blue">Da li ste sigurni da želite da deaktivirate ovaj artikal?</h2>
                <p className="mt-2 text-graphite/65"><strong>{product.name}</strong> ({product.code}) više neće biti dostupan za nova trebovanja. Deaktivacija je trajna.</p>
                {error && <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
                <div className="mt-6 flex justify-end gap-3">
                    <button disabled={submitting} onClick={onCancel} className="rounded-xl border border-dust-grey px-5 py-2.5 font-bold disabled:opacity-50">Ne</button>
                    <button disabled={submitting} onClick={onConfirm} className="rounded-xl bg-red-700 px-5 py-2.5 font-bold text-white hover:bg-red-800 disabled:opacity-50">{submitting ? "Čuvanje..." : "Da"}</button>
                </div>
            </div>
        </div>
    );
}
