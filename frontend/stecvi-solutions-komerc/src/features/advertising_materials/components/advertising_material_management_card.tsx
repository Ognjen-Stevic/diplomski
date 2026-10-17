import { Package, Power } from "lucide-react";
import type { AdvertisingMaterial } from "../types/advertising_material.types";

export function AdvertisingMaterialManagementCard({
    material,
    changingStatus,
    onChangeStatus,
}: {
    material: AdvertisingMaterial;
    changingStatus: boolean;
    onChangeStatus: () => void;
}) {
    return (
        <article className="rounded-2xl border border-dust-grey bg-white p-5 shadow-sm">
            <div className="flex items-start gap-4">
                <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-yale-blue/10 text-yale-blue"><Package className="h-6 w-6" /></span>
                <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                        <h2 className="text-lg font-bold text-yale-blue">{material.name}</h2>
                        <span className={`rounded-full px-2.5 py-1 text-xs font-bold ring-1 ring-inset ${material.active ? "bg-emerald-50 text-emerald-800 ring-emerald-200" : "bg-slate-100 text-slate-600 ring-slate-200"}`}>{material.active ? "Aktivan" : "Neaktivan"}</span>
                    </div>
                </div>
            </div>
            <button type="button" disabled={changingStatus} onClick={onChangeStatus} className={`mt-5 inline-flex w-full items-center justify-center gap-2 rounded-xl border px-4 py-2.5 text-sm font-bold disabled:opacity-50 ${material.active ? "border-red-200 text-red-700 hover:bg-red-50" : "border-emerald-200 text-emerald-700 hover:bg-emerald-50"}`}><Power className="h-4 w-4" />{material.active ? "Deaktiviraj" : "Aktiviraj"}</button>
        </article>
    );
}
