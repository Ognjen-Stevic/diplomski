import { useEffect } from "react";
import { X } from "lucide-react";

import type { GoodsRequestRecord, ProcessGoodsRequestStatus } from "../types/goods_request.types";

export function GoodsRequestStatusDialog({ request, status, submitting, error, onCancel, onConfirm }: {
    request: GoodsRequestRecord;
    status: ProcessGoodsRequestStatus;
    submitting: boolean;
    error: string;
    onCancel: () => void;
    onConfirm: () => void;
}) {
    const approving = status === "APPROVED";

    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape" && !submitting) onCancel();
        };
        document.addEventListener("keydown", handleKeyDown);
        document.body.style.overflow = "hidden";
        return () => {
            document.removeEventListener("keydown", handleKeyDown);
            document.body.style.overflow = "";
        };
    }, [onCancel, submitting]);

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-graphite/45 p-4" role="dialog" aria-modal="true" aria-labelledby="goods-request-dialog-title">
            <section className="w-full max-w-md rounded-2xl bg-white p-5 shadow-2xl sm:p-6">
                <header className="flex items-start justify-between gap-4">
                    <div>
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Zahtev #{request.id}</p>
                        <h2 id="goods-request-dialog-title" className="mt-1 text-xl font-bold text-yale-blue">
                            {approving ? "Odobri zahtev za robu" : "Odbij zahtev za robu"}
                        </h2>
                    </div>
                    <button type="button" onClick={onCancel} disabled={submitting} aria-label="Zatvori" className="rounded-lg p-2 text-graphite hover:bg-dust-grey/30 disabled:opacity-50">
                        <X className="h-5 w-5" aria-hidden="true" />
                    </button>
                </header>

                <p className="mt-4 text-sm leading-6 text-graphite/70">
                    Komercijalista: <strong className="text-graphite">{request.salesRep.firstName} {request.salesRep.lastName}</strong>.
                    {" "}Biće {approving ? "odobrene" : "odbijene"} sve stavke koje su još na čekanju.
                </p>
                {error && <p role="alert" className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

                <footer className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                    <button type="button" onClick={onCancel} disabled={submitting} className="rounded-xl border border-dust-grey px-4 py-2.5 font-semibold text-graphite disabled:opacity-50">
                        Odustani
                    </button>
                    <button type="button" onClick={onConfirm} disabled={submitting} className={`rounded-xl px-4 py-2.5 font-semibold text-white disabled:cursor-wait disabled:opacity-60 ${approving ? "bg-emerald-700 hover:bg-emerald-800" : "bg-red-700 hover:bg-red-800"}`}>
                        {submitting ? "Obrada..." : approving ? "Potvrdi odobrenje" : "Potvrdi odbijanje"}
                    </button>
                </footer>
            </section>
        </div>
    );
}
