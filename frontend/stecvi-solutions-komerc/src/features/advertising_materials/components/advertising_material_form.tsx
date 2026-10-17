import { useState, type FormEvent } from "react";
import { Save, X } from "lucide-react";

export function AdvertisingMaterialForm({ saving, error, onCancel, onSubmit }: {
    saving: boolean;
    error: string;
    onCancel: () => void;
    onSubmit: (name: string) => Promise<void>;
}) {
    const [name, setName] = useState("");
    const [validationError, setValidationError] = useState("");
    const submit = (event: FormEvent) => {
        event.preventDefault();
        const normalizedName = name.trim();
        if (!normalizedName) {
            setValidationError("Naziv reklamnog materijala je obavezan.");
            return;
        }
        setValidationError("");
        return onSubmit(normalizedName);
    };
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4" role="dialog" aria-modal="true" aria-labelledby="material-form-title">
            <form onSubmit={submit} className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-xl sm:p-7">
                <div className="flex items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Upravljanje materijalom</p><h2 id="material-form-title" className="mt-1 text-2xl font-bold text-yale-blue">Novi reklamni materijal</h2></div><button type="button" onClick={onCancel} aria-label="Zatvori" className="rounded-lg p-2 text-graphite/60 hover:bg-dust-grey/50"><X className="h-5 w-5" /></button></div>
                <label className="mt-6 block font-semibold text-graphite">Naziv *<input required maxLength={150} value={name} onChange={(event) => { setName(event.target.value); setValidationError(""); }} className="mt-2 w-full rounded-xl border border-dust-grey px-4 py-3 outline-none focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20" /></label>
                {(validationError || error) && <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-red-700">{validationError || error}</p>}
                <div className="mt-7 flex justify-end gap-3"><button type="button" onClick={onCancel} className="rounded-xl border border-dust-grey px-5 py-3 font-semibold">Otkaži</button><button disabled={saving} className="inline-flex items-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-bold text-white hover:bg-stormy-teal disabled:opacity-50"><Save className="h-5 w-5" />{saving ? "Čuvanje..." : "Sačuvaj"}</button></div>
            </form>
        </div>
    );
}
