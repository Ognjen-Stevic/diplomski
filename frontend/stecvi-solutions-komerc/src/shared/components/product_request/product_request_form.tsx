import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { PackagePlus } from "lucide-react";

import { RequestConfirmationDialog, type ConfirmationContext } from "./request_confirmation_dialog";
import { RequestItemsTable } from "./request_items_table";
import { RequestNoteField } from "./request_note_field";
import type { RequestCatalogItem } from "../../types/request_catalog_item";

export type SelectedProduct<T extends RequestCatalogItem = RequestCatalogItem> = {
    product: T;
    quantity: number;
};

type ProductRequestFormProps<T extends RequestCatalogItem> = {
    children?: ReactNode;
    products?: readonly T[];
    itemsTitle?: string;
    itemsDescription?: string;
    selectedItemsTitle?: string;
    submitLabel: string;
    confirmationTitle: string;
    confirmationDescription: string;
    confirmationContext?: ConfirmationContext;
    validateBeforeConfirmation?: () => boolean;
    initialQuantities?: Readonly<Record<string, number>>;
    initialNote?: string;
    onCancel: () => void;
    onConfirm: (data: { products: SelectedProduct<T>[]; note: string | null }) => Promise<void>;
};

const getQuantityError = (value: string, product: RequestCatalogItem) => (
    /^\d+$/.test(value) && Number.isSafeInteger(Number(value))
        && (product.active !== false || Number(value) > 0)
        ? ""
        : product.active === false
            ? "Neaktivan artikal mora ostati u trebovanju."
            : "Unesite ceo broj 0 ili veći."
);

export function ProductRequestForm<T extends RequestCatalogItem>({
    children,
    products = [],
    itemsTitle = "Proizvodi",
    itemsDescription = "Unesite potrebne količine.",
    selectedItemsTitle = "Izabrani proizvodi",
    submitLabel,
    confirmationTitle,
    confirmationDescription,
    confirmationContext,
    validateBeforeConfirmation,
    initialQuantities = {},
    initialNote = "",
    onCancel,
    onConfirm,
}: ProductRequestFormProps<T>) {
    const [quantities, setQuantities] = useState<string[]>(() => products.map((product) => String(initialQuantities[product.key] ?? 0)));
    const [touchedQuantities, setTouchedQuantities] = useState<boolean[]>(() => products.map(() => false));
    const [note, setNote] = useState(initialNote);
    const [submitted, setSubmitted] = useState(false);
    const [confirmationOpen, setConfirmationOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [submissionError, setSubmissionError] = useState("");

    const quantityErrors = useMemo(
        () => quantities.map((quantity, index) => getQuantityError(quantity, products[index])),
        [products, quantities],
    );
    const selectedProducts = useMemo(
        () => products.map((product, index) => ({
            product,
            quantity: Number(quantities[index]),
        })).filter((item) => item.quantity > 0),
        [products, quantities],
    );
    const hasSelectedProduct = selectedProducts.length > 0;

    useEffect(() => {
        if (!confirmationOpen) return;

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape" && !submitting) setConfirmationOpen(false);
        };

        document.addEventListener("keydown", handleKeyDown);
        document.body.style.overflow = "hidden";

        return () => {
            document.removeEventListener("keydown", handleKeyDown);
            document.body.style.overflow = "";
        };
    }, [confirmationOpen, submitting]);

    const updateQuantity = (index: number, value: string) => {
        setQuantities((current) => current.map((quantity, itemIndex) => (
            itemIndex === index ? value : quantity
        )));
        setTouchedQuantities((current) => current.map((isTouched, itemIndex) => (
            itemIndex === index ? true : isTouched
        )));
        setSubmitted(false);
    };

    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setSubmitted(true);

        const parentFieldsAreValid = validateBeforeConfirmation?.() ?? true;
        if (quantityErrors.some(Boolean) || !hasSelectedProduct || !parentFieldsAreValid) return;

        setConfirmationOpen(true);
    };

    const handleConfirm = async () => {
        if (submitting) return;

        setSubmitting(true);
        setSubmissionError("");
        try {
            await onConfirm({
                products: selectedProducts,
                note: note.trim() || null,
            });
            setConfirmationOpen(false);
        } catch (error) {
            setSubmissionError(
                error instanceof Error ? error.message : "Zahtev nije sačuvan.",
            );
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <>
            <form onSubmit={handleSubmit} noValidate className="mt-5 space-y-5">
                {children}
                <RequestItemsTable
                    products={products}
                    title={itemsTitle}
                    description={itemsDescription}
                    quantities={quantities}
                    quantityErrors={quantityErrors}
                    touchedQuantities={touchedQuantities}
                    submitted={submitted}
                    hasSelectedProduct={hasSelectedProduct}
                    onQuantityChange={updateQuantity}
                    onQuantityBlur={(index) => setTouchedQuantities((current) => current.map((value, itemIndex) => (
                        itemIndex === index ? true : value
                    )))}
                />
                <RequestNoteField value={note} onChange={setNote} />

                <footer className="flex flex-col-reverse gap-3 border-t border-graphite/10 pt-5 sm:flex-row sm:justify-end">
                    <button
                        type="button"
                        onClick={onCancel}
                        className="rounded-xl border border-dust-grey bg-white px-5 py-3 font-semibold text-graphite transition hover:bg-dust-grey/30 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                    >
                        Odustani
                    </button>
                    <button
                        type="submit"
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-semibold text-white shadow-sm transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2"
                    >
                        <PackagePlus className="h-5 w-5" />
                        {submitLabel}
                    </button>
                </footer>
            </form>

            {confirmationOpen && (
                <RequestConfirmationDialog
                    title={confirmationTitle}
                    description={confirmationDescription}
                    selectedItemsTitle={selectedItemsTitle}
                    context={confirmationContext}
                    products={selectedProducts}
                    note={note}
                    submitting={submitting}
                    error={submissionError}
                    onCancel={() => {
                        if (!submitting) setConfirmationOpen(false);
                    }}
                    onConfirm={() => void handleConfirm()}
                />
            )}
        </>
    );
}
