import { useRequisition, useRequisitionMutationScope } from "../hooks/use_requisition_data";
import { useRequisitionReturnDestination } from "../hooks/use_requisition_route";
import { requisitionPayload } from "../utils/requisition_payload";
import { authApi } from "../../auth/auth_api";
import { APP_ROUTES } from "../../../app/routes";
import { useMemo, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { Navigate, useNavigate, useParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { ProductRequestForm } from "../../../shared/components/product_request/product_request_form";
import { useProducts } from "../../product/hooks/use_products";
import { useAuth } from "../../auth/hooks/useAuth";
import { RequisitionTypeSection } from "../components/requisition_type_section";
import { requisitionApi } from "../requisition_api";
import type { GratisReason, RequisitionListItem } from "../types/requisition.types";

export default function EditRequisitionPage() {
    const navigate = useNavigate();
    const { requisitionId: requisitionIdParam } = useParams();
    const requisitionId = Number(requisitionIdParam);
    const { user } = useAuth();
    const canUseEditPage = user?.role === "SALES_REP" || user?.role === "MANAGER";
    const query = useRequisition(requisitionId, canUseEditPage);
    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (!canUseEditPage || !Number.isSafeInteger(requisitionId) || requisitionId <= 0) return <Navigate to={APP_ROUTES.requisitions} replace />;
    if (query.loading || !query.data) return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-5 sm:px-6 sm:pt-7 lg:px-8 lg:pb-12">
                <div className="mx-auto w-full max-w-5xl">
                    <button type="button" onClick={() => navigate(-1)} className="inline-flex items-center gap-2 text-sm font-semibold text-stormy-teal"><ArrowLeft className="h-4 w-4" />Nazad</button>
                    <header className="mt-4 border-b border-graphite/10 pb-5"><p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Trebovanja</p><h1 className="mt-1 text-3xl font-bold text-yale-blue">Izmeni trebovanje</h1></header>
                    {query.error ? <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-red-700">{query.error}<button onClick={query.retry} className="ml-3 font-bold underline">Pokušaj ponovo</button></p>
                        : <p className="mt-8 text-center text-graphite/60">Učitavanje trebovanja...</p>}
                </div>
            </main>
        </AppLayout>
    );
    return <EditRequisitionForm key={`${authApi.getToken()}:${requisitionId}`} requisition={query.data} />;
}

function EditRequisitionForm({ requisition }: { requisition: RequisitionListItem }) {
    const navigate = useNavigate();
    const returnDestination = useRequisitionReturnDestination();
    const isCurrent = useRequisitionMutationScope();
    const { user } = useAuth();
    const canUseEditPage = user?.role === "SALES_REP" || user?.role === "MANAGER";
    const requisitionType = requisition.type;
    const [gratisReason, setGratisReason] = useState<GratisReason>(requisition.gratisReason ?? "");
    const [customReason, setCustomReason] = useState(requisition.customReason ?? "");
    const [submitted, setSubmitted] = useState(false);
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

    const validateDetails = () => {
        setSubmitted(true);
        return requisitionType !== "gratis" || Boolean(gratisReason && (gratisReason !== "OTHER" || customReason.trim()));
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

                    {!requisition.actions.canEdit && <p role="alert" className="mt-6 text-red-700">Ovo trebovanje više nije moguće izmeniti.</p>}
                    {productsLoading && <p className="mt-8 text-center text-graphite/60">Učitavanje trebovanja...</p>}
                    {productsError && <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-red-700">{productsError}</p>}
                    {!productsLoading && !productsError && requisition?.actions.canEdit && (
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
                                await requisitionApi.update(requisition.id, requisitionPayload({
                                    customerId: requisition.buyerId, type: requisition.type, reason: gratisReason,
                                    customReason, note, expectedRevision: requisition.revision,
                                    items: selectedProducts.map(({ product, quantity }) => ({ productCode: product.code, quantity })),
                                }));
                                if (isCurrent()) navigate(returnDestination, { replace: true });
                            }}
                        >
                            <RequisitionTypeSection
                                requisitionType={requisitionType}
                                gratisReason={gratisReason}
                                customReason={customReason}
                                submitted={submitted}
                                typeDisabled
                                onTypeChange={() => {}}
                                onReasonChange={(reason) => { setGratisReason(reason); setSubmitted(false); }}
                                onCustomReasonChange={(value) => { setCustomReason(value); setSubmitted(false); }}
                            />
                        </ProductRequestForm>
                    )}
                </div>
            </main>
        </AppLayout>
    );
}
