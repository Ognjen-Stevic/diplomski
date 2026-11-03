import { Package, Power } from "lucide-react";
import type { ManagedProduct } from "../types/product_management.types";
import { resolveProductPictureUrl } from "../../../shared/utils/asset_url";

const money = (value: number) => value.toLocaleString("sr-Latn-RS", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
});

export function ProductManagementCard({
    product,
    deactivating,
    onDeactivate,
    readOnly = false,
}: {
    product: ManagedProduct;
    deactivating: boolean;
    onDeactivate: () => void;
    readOnly?: boolean;
}) {
    return (
        <article className="rounded-2xl border border-dust-grey bg-white p-5 shadow-sm">
            <div className="flex items-start gap-4">
                <span className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-yale-blue/10 text-yale-blue">
                    {product.pictureUrl ? <img src={resolveProductPictureUrl(product.pictureUrl)} alt="" className="h-full w-full object-cover" /> : <Package className="h-6 w-6" />}
                </span>
                <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                        <div><h2 className="text-lg font-bold text-yale-blue">{product.name}</h2><p className="mt-0.5 text-sm font-semibold text-stormy-teal">{product.code}</p></div>
                        <span className={`rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${product.active ? "bg-emerald-50 text-emerald-800 ring-emerald-200" : "bg-slate-100 text-slate-600 ring-slate-200"}`}>{product.active ? "Aktivan" : "Neaktivan"}</span>
                    </div>
                </div>
            </div>
            <dl className="mt-5 grid grid-cols-2 gap-x-4 gap-y-3 border-t border-dust-grey pt-4 text-sm">
                <div><dt className="text-graphite/55">Cena</dt><dd className="mt-0.5 font-bold text-graphite">{money(product.price)} RSD</dd></div>
                <div><dt className="text-graphite/55">{readOnly ? "Količina" : "Zapremina pakovanja"}</dt><dd className="mt-0.5 font-bold text-graphite">{product.volumeLiters} l</dd></div>
                <div><dt className="text-graphite/55">Stimulacija komercijaliste</dt><dd className="mt-0.5 font-bold text-graphite">{money(product.stimulationPerUnit)} RSD/kom</dd></div>
                <div><dt className="text-graphite/55">Stimulacija menadžera</dt><dd className="mt-0.5 font-bold text-graphite">{money(product.managerStimulationPerUnit)} RSD/kom</dd></div>
            </dl>
            {!readOnly && product.active && <button type="button" disabled={deactivating} onClick={onDeactivate} className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-xl border border-red-200 px-4 py-2.5 text-sm font-bold text-red-700 hover:bg-red-50 disabled:opacity-50"><Power className="h-4 w-4" />Deaktiviraj</button>}
        </article>
    );
}
