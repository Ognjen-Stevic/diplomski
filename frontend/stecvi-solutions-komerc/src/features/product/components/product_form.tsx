import { useDialog } from "../../../shared/hooks/use_dialog";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Save, X } from "lucide-react";
import type { ProductCreatePayload } from "../types/product_management.types";

const inputClass = "mt-2 w-full rounded-xl border border-dust-grey bg-white px-4 py-3 text-graphite outline-none focus:border-stormy-teal focus:ring-2 focus:ring-stormy-teal/20";

export function ProductForm({
    saving,
    error,
    onCancel,
    onSubmit,
}: {
    saving: boolean;
    error: string;
    onCancel: () => void;
    onSubmit: (payload: ProductCreatePayload, image: File | null) => Promise<void>;
}) {
    const dialog = useDialog(onCancel, saving);
    const [name, setName] = useState("");
    const [code, setCode] = useState("");
    const [price, setPrice] = useState("");
    const [volumeLiters, setVolumeLiters] = useState("");
    const [salesRepStimulation, setSalesRepStimulation] = useState("");
    const [managerStimulation, setManagerStimulation] = useState("");
    const [image, setImage] = useState<File | null>(null);
    const imagePreview = useMemo(() => image ? URL.createObjectURL(image) : null, [image]);

    useEffect(() => () => { if (imagePreview) URL.revokeObjectURL(imagePreview); }, [imagePreview]);

    const submit = (event: FormEvent) => {
        event.preventDefault();
        return onSubmit({
            name: name.trim(),
            code: code.trim(),
            price: Number(price),
            volumeLiters: Number(volumeLiters),
            stimulationPerUnit: Number(salesRepStimulation),
            managerStimulationPerUnit: Number(managerStimulation),
        }, image);
    };

    return (
        <div ref={dialog} tabIndex={-1} className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4" role="dialog" aria-modal="true" aria-labelledby="product-form-title">
            <form onSubmit={submit} className="max-h-[calc(100vh-2rem)] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-xl sm:p-7">
                <div className="flex items-start justify-between gap-4">
                    <div><p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Upravljanje artiklima</p><h2 id="product-form-title" className="mt-1 text-2xl font-bold text-yale-blue">Novi artikal</h2></div>
                    <button type="button" onClick={onCancel} aria-label="Zatvori" className="rounded-lg p-2 text-graphite/60 hover:bg-dust-grey/50"><X className="h-5 w-5" /></button>
                </div>
                <div className="mt-6 grid gap-5 sm:grid-cols-2">
                    <Field label="Naziv *" value={name} onChange={setName} maxLength={200} />
                    <Field label="Šifra *" value={code} onChange={setCode} maxLength={100} />
                    <NumberField label="Cena (RSD) *" value={price} onChange={setPrice} min="0.01" step="0.01" />
                    <NumberField label="Zapremina pakovanja (l) *" value={volumeLiters} onChange={setVolumeLiters} min="0.001" step="0.001" />
                    <NumberField label="Stimulacija SALES_REP po komadu (RSD) *" value={salesRepStimulation} onChange={setSalesRepStimulation} min="0" step="0.01" />
                    <NumberField label="Stimulacija MANAGER po komadu (RSD) *" value={managerStimulation} onChange={setManagerStimulation} min="0" step="0.01" />
                    <label className="font-semibold text-graphite sm:col-span-2">Slika artikla
                        <input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => setImage(event.target.files?.[0] ?? null)} className={`${inputClass} file:mr-4 file:rounded-lg file:border-0 file:bg-yale-blue/10 file:px-3 file:py-2 file:font-bold file:text-yale-blue`} />
                        <span className="mt-2 block text-xs font-normal text-graphite/55">JPEG, PNG ili WebP, najviše 5 MB. Ako slika nije izabrana, koristi se podrazumevana slika.</span>
                    </label>
                    {imagePreview && <div className="sm:col-span-2"><p className="mb-2 font-semibold text-graphite">Pregled slike</p><img src={imagePreview} alt="Pregled izabrane slike artikla" className="h-40 w-40 rounded-xl border border-dust-grey object-cover" /></div>}
                </div>
                {error && <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-red-700">{error}</p>}
                <div className="mt-7 flex justify-end gap-3">
                    <button type="button" onClick={onCancel} className="rounded-xl border border-dust-grey px-5 py-3 font-semibold">Otkaži</button>
                    <button disabled={saving} className="inline-flex items-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-bold text-white hover:bg-stormy-teal disabled:opacity-50"><Save className="h-5 w-5" />{saving ? "Čuvanje..." : "Sačuvaj artikal"}</button>
                </div>
            </form>
        </div>
    );
}

function Field({ label, value, onChange, maxLength }: { label: string; value: string; onChange: (value: string) => void; maxLength: number }) {
    return <label className="font-semibold text-graphite">{label}<input required maxLength={maxLength} value={value} onChange={(event) => onChange(event.target.value)} className={inputClass} /></label>;
}

function NumberField({ label, value, onChange, min, step }: { label: string; value: string; onChange: (value: string) => void; min: string; step: string }) {
    return <label className="font-semibold text-graphite">{label}<input required type="number" min={min} step={step} value={value} onChange={(event) => onChange(event.target.value)} className={inputClass} /></label>;
}
