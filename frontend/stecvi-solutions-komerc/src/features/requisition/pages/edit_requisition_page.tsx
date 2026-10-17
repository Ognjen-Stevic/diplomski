import { useEffect, useMemo, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { Navigate, useNavigate, useParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { ProductRequestForm } from "../../../shared/components/product_request/product_request_form";
import { useProducts } from "../../../shared/hooks/use_products";
import { useAuth } from "../../auth/hooks/useAuth";
import { RequisitionTypeSection } from "../components/requisition_type_section";
import { requisitionApi, type CreateRequisitionRequest } from "../requisition_api";
import type { GratisReason, RequisitionListItem, RequisitionType } from "../types/requisition.types";

const REASON_FROM_API = {
    LISTING: "Za ulistavanje",
    CONSUMPTION: "Za potrošnju",
    GIFT: "Poklon",
    OTHER: "Drugo",
} as const;

export default function EditRequisitionPage() {
    const navigate = useNavigate();
    const { requisitionId: requisitionIdParam } = useParams();
    const requisitionId = Number(requisitionIdParam);
    const { user } = useAuth();
    const canUseEditPage = user?.role === "SALES_REP" || user?.role === "MANAGER";
    const [requisition, setRequisition] = useState<RequisitionListItem | null>(null);
    const [requisitionType, setRequisitionType] = useState<RequisitionType>("classic");
    const [gratisReason, setGratisReason] = useState<GratisReason>("");
    const [customReason, setCustomReason] = useState("");
    const [submitted, setSubmitted] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const { products, loading: productsLoading, error: productsError } = useProducts(canUseEditPage);
    const editableProducts = useMemo(() => {
        if (!requisition) return products;
        const activeCodes = new Set(products.map((product) => product.code));
        const inactiveExistingProducts = requisition.items
            .filter((item) => !item.active && !activeCodes.has(item.productCode))
            .map((item) => ({
                key: item.productCode,
                code: item.productCode,
                name: item.productName,
                price: item.price,
                pictureUrl: item.pictureUrl,
                active: false,
                imageUrl: item.pictureUrl,
                details: `${item.productCode} · ${item.price.toLocaleString("sr-Latn-RS", {
                    minimumFractionDigits: 2,
                })} RSD`,
            }));
        return [...inactiveExistingProducts, ...products];
    }, [products, requisition]);

    useEffect(() => {
        if (!user || !Number.isInteger(requisitionId) || requisitionId <= 0) return;
        let active = true;
        requisitionApi.findById(requisitionId)
            .then((item) => {
                if (!active) return;
                setRequisition(item);
                setRequisitionType(item.type);
                setGratisReason(item.gratisReason ? REASON_FROM_API[item.gratisReason] : "");
                setCustomReason(item.customReason ?? "");
                if (!item.actions.canEdit) setError("Ovo trebovanje više nije moguće izmeniti.");
            })
            .catch((requestError: unknown) => setError(requestError instanceof Error ? requestError.message : "Trebovanje nije učitano."))
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [requisitionId, user]);

    if (!user) return <Navigate to="/login" replace />;
    if (!canUseEditPage || !Number.isInteger(requisitionId) || requisitionId <= 0) return <Navigate to="/trebovanja" replace />;

    const validateDetails = () => {
        setSubmitted(true);
        return requisitionType !== "gratis" || Boolean(gratisReason && (gratisReason !== "Drugo" || customReason.trim()));
    };

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-5 sm:px-6 sm:pt-7 lg:px-8 lg:pb-12">
                <div className="mx-auto w-full max-w-5xl">
                    <button type="button" onClick={() => navigate(-1)} className="inline-flex items-center gap-2 text-sm font-semibold text-yale-blue transition hover:text-stormy-teal"><ArrowLeft className="h-4 w-4" />Nazad</button>
                    <header className="mt-4 border-b border-graphite/10 pb-5">
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Trebovanja</p>
                        <h1 className="mt-1 text-3xl font-bold text-yale-blue">Izmeni trebovanje</h1>
                        {requisition && <p className="mt-2 text-sm text-graphite/70">Kupac: <strong>{requisition.buyerName}</strong> · Vrsta: <strong>{requisition.type === "gratis" ? "Gratis" : "Klasično"}</strong></p>}
                    </header>

                    {(loading || productsLoading) && <p className="mt-8 text-center text-graphite/60">Učitavanje trebovanja...</p>}
                    {(error || productsError) && <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-red-700">{error || productsError}</p>}
                    {!loading && !productsLoading && !error && !productsError && requisition?.actions.canEdit && (
                        <ProductRequestForm
                            products={editableProducts}
                            initialQuantities={Object.fromEntries(requisition.items.map((item) => [item.productCode, item.quantity]))}
                            initialNote={requisition.note ?? ""}
                            submitLabel="Sačuvaj izmene"
                            confirmationTitle="Potvrda izmene"
                            confirmationDescription="Proverite podatke pre čuvanja izmena."
                            validateBeforeConfirmation={validateDetails}
                            onCancel={() => navigate(-1)}
                            onConfirm={async ({ products: selectedProducts, note }) => {
                                const reasonMap = { "Za ulistavanje": "LISTING", "Za potrošnju": "CONSUMPTION", Poklon: "GIFT", Drugo: "OTHER" } as const;
                                const payload: CreateRequisitionRequest = {
                                    customerId: requisition.buyerId,
                                    type: requisition.type === "gratis" ? "GRATIS" : "NORMAL",
                                    gratisReason: requisition.type === "gratis" ? reasonMap[gratisReason as Exclude<GratisReason, "">] : null,
                                    customReason: requisition.type === "gratis" && gratisReason === "Drugo" ? customReason.trim() : null,
                                    note,
                                    items: selectedProducts.map(({ product, quantity }) => ({ productCode: product.code, quantity })),
                                };
                                setSubmitting(true);
                                try {
                                    await requisitionApi.update(requisition.id, payload);
                                    navigate("/trebovanja", { replace: true });
                                } catch (requestError) {
                                    setError(requestError instanceof Error ? requestError.message : "Izmene nisu sačuvane.");
                                    throw requestError;
                                } finally {
                                    setSubmitting(false);
                                }
                            }}
                        >
                            <RequisitionTypeSection
                                requisitionType={requisitionType}
                                gratisReason={gratisReason}
                                customReason={customReason}
                                submitted={submitted}
                                typeDisabled
                                onTypeChange={setRequisitionType}
                                onReasonChange={(reason) => { setGratisReason(reason); setSubmitted(false); }}
                                onCustomReasonChange={(value) => { setCustomReason(value); setSubmitted(false); }}
                            />
                        </ProductRequestForm>
                    )}
                    {submitting && <p className="mt-4 text-center font-semibold text-stormy-teal">Čuvanje trebovanja...</p>}
                </div>
            </main>
        </AppLayout>
    );
}
