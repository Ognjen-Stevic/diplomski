import { Package, Power } from "lucide-react";
import { useState } from "react";
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
    const [failedPictureUrl, setFailedPictureUrl] = useState<string | null>(null);
    const pictureUrl = product.pictureUrl
        ? resolveProductPictureUrl(product.pictureUrl)
        : null;

    return (
        <article className="flex h-full flex-col rounded-2xl border border-dust-grey bg-white p-5 shadow-sm">
            <div className="flex h-52 w-full items-center justify-center overflow-hidden rounded-xl bg-white text-yale-blue">
                {pictureUrl && failedPictureUrl !== pictureUrl ? (
                    <img
                        src={pictureUrl}
                        alt=""
                        loading="lazy"
                        decoding="async"
                        onError={() => setFailedPictureUrl(pictureUrl)}
                        className="h-full w-full object-contain"
                    />
                ) : (
                    <Package className="h-10 w-10" aria-hidden="true" />
                )}
            </div>

            <div className="mt-5 flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                    <h2 className="text-lg font-bold text-yale-blue">{product.name}</h2>
                    <p className="mt-0.5 text-sm font-semibold text-stormy-teal">{product.code}</p>
                </div>
                <span className={`rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${product.active ? "bg-emerald-50 text-emerald-800 ring-emerald-200" : "bg-slate-100 text-slate-600 ring-slate-200"}`}>{product.active ? "Aktivan" : "Neaktivan"}</span>
            </div>
            <dl className="mt-5 grid grid-cols-2 gap-x-4 gap-y-3 border-t border-dust-grey pt-4 text-sm">
                <div><dt className="text-graphite/55">Cena</dt><dd className="mt-0.5 font-bold text-graphite">{money(product.price)} RSD</dd></div>
                <div><dt className="text-graphite/55">{readOnly ? "Količina" : "Zapremina pakovanja"}</dt><dd className="mt-0.5 font-bold text-graphite">{product.volumeLiters} l</dd></div>
                <div><dt className="text-graphite/55">Stimulacija komercijaliste</dt><dd className="mt-0.5 font-bold text-graphite">{money(product.stimulationPerUnit)} RSD/kom</dd></div>
                <div><dt className="text-graphite/55">Stimulacija menadžera</dt><dd className="mt-0.5 font-bold text-graphite">{money(product.managerStimulationPerUnit)} RSD/kom</dd></div>
            </dl>
            {!readOnly && product.active && <div className="mt-auto pt-5"><button type="button" disabled={deactivating} onClick={onDeactivate} className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-red-200 px-4 py-2.5 text-sm font-bold text-red-700 hover:bg-red-50 disabled:opacity-50"><Power className="h-4 w-4" />Deaktiviraj</button></div>}
        </article>
    );
}
