import { Check, ClipboardList, X } from "lucide-react";

import type { SelectedProduct } from "./product_request_form";
import type { RequestCatalogItem } from "../../types/request_catalog_item";

export type ConfirmationContext = {
    label: string;
    primaryText: string;
    secondaryText?: string;
};

type RequestConfirmationDialogProps<T extends RequestCatalogItem> = {
    title: string;
    description: string;
    selectedItemsTitle: string;
    context?: ConfirmationContext;
    products: SelectedProduct<T>[];
    note: string;
    submitting: boolean;
    error: string;
    onCancel: () => void;
    onConfirm: () => void;
};

export function RequestConfirmationDialog<T extends RequestCatalogItem>({
    title,
    description,
    selectedItemsTitle,
    context,
    products,
    note,
    submitting,
    error,
    onCancel,
    onConfirm,
}: RequestConfirmationDialogProps<T>) {
    const totalQuantity = products.reduce((total, item) => total + item.quantity, 0);

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-graphite/55 p-4 backdrop-blur-sm"
            role="dialog"
            aria-modal="true"
            aria-labelledby="product-request-confirmation-title"
        >
            <button type="button" disabled={submitting} aria-label="Zatvori potvrdu" onClick={onCancel} className="absolute inset-0 cursor-default" />

            <section className="relative z-10 flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl">
                <header className="flex items-start justify-between gap-4 border-b border-dust-grey px-5 py-5 sm:px-6">
                    <div>
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Završna provera</p>
                        <h2 id="product-request-confirmation-title" className="mt-1 text-2xl font-bold text-yale-blue">{title}</h2>
                        <p className="mt-1 text-sm text-graphite/60">{description}</p>
                    </div>
                    <button
                        type="button"
                        disabled={submitting}
                        onClick={onCancel}
                        aria-label="Zatvori"
                        className="rounded-xl p-2 text-graphite transition hover:bg-dust-grey/35 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </header>

                <div className="min-h-0 flex-1 overflow-y-auto px-5 py-5 sm:px-6">
                    {context && (
                        <div className="mb-5 rounded-xl border border-stormy-teal/25 bg-stormy-teal/[0.06] px-4 py-3">
                            <p className="text-xs font-semibold uppercase tracking-wide text-graphite/50">{context.label}</p>
                            <p className="mt-0.5 font-bold text-graphite">{context.primaryText}</p>
                            {context.secondaryText && <p className="text-sm text-graphite/60">{context.secondaryText}</p>}
                        </div>
                    )}

                    <div className="flex items-center justify-between gap-3">
                        <h3 className="flex items-center gap-2 font-bold text-yale-blue">
                            <ClipboardList className="h-4 w-4" />
                            {selectedItemsTitle}
                        </h3>
                        <span className="text-sm font-medium text-graphite/55">Ukupna količina: {totalQuantity}</span>
                    </div>

                    <div className="mt-3 overflow-hidden rounded-xl border border-dust-grey">
                        <table className="w-full text-left">
                            <thead className="bg-yale-blue/[0.04] text-xs uppercase tracking-wider text-graphite/55">
                                <tr>
                                    <th className="px-4 py-3 font-bold">Stavka</th>
                                    <th className="px-4 py-3 text-right font-bold">Količina</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-dust-grey/75">
                                {products.map((item) => (
                                    <tr key={item.product.key}>
                                        <td className="px-4 py-3 text-sm font-medium text-graphite">{item.product.name}</td>
                                        <td className="px-4 py-3 text-right font-bold tabular-nums text-yale-blue">{item.quantity}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {note.trim() && (
                        <div className="mt-4 rounded-xl bg-dust-grey/25 px-4 py-3">
                            <p className="text-xs font-semibold uppercase tracking-wide text-graphite/50">Napomena</p>
                            <p className="mt-1 whitespace-pre-wrap text-sm text-graphite">{note.trim()}</p>
                        </div>
                    )}
                </div>

                {error && (
                    <p role="alert" className="mx-5 mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700 sm:mx-6">
                        {error}
                    </p>
                )}

                <footer className="flex flex-col-reverse gap-3 border-t border-dust-grey px-5 py-4 sm:flex-row sm:justify-end sm:px-6">
                    <button
                        type="button"
                        disabled={submitting}
                        onClick={onCancel}
                        className="rounded-xl border border-dust-grey px-5 py-3 font-semibold text-graphite transition hover:bg-dust-grey/30 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                    >
                        Nazad na izmenu
                    </button>
                    <button
                        type="button"
                        disabled={submitting}
                        onClick={onConfirm}
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white shadow-sm transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2"
                    >
                        <Check className="h-5 w-5" />
                        {submitting ? "Čuvanje..." : "Da, siguran sam"}
                    </button>
                </footer>
            </section>
        </div>
    );
}
