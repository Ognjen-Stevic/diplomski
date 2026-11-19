import { useRequisitionMutationScope } from "../hooks/use_requisition_data";
import { requisitionPayload } from "../utils/requisition_payload";
import { authApi } from "../../auth/auth_api";
import { APP_ROUTES } from "../../../app/routes";
import { useMemo, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { useCustomerSelector } from "../../customer/use_customer_selector";
import { CustomerSelector } from "../../../shared/components/customer_selector";
import { ProductRequestForm } from "../../../shared/components/product_request/product_request_form";
import { RequisitionTypeSection } from "../components/requisition_type_section";
import type { GratisReason, RequisitionType } from "../types/requisition.types";
import { useAuth } from "../../auth/hooks/useAuth";
import { PERMISSIONS } from "../../../roles/permissions";
import { requisitionApi } from "../requisition_api";
import { useProducts } from "../../product/hooks/use_products";

type ProductCategory = "ALL" | "BOTTLE" | "JUG";

const PRODUCT_CATEGORIES: ReadonlyArray<{ value: ProductCategory; label: string }> = [
    { value: "ALL", label: "Sve" },
    { value: "BOTTLE", label: "Flaše" },
    { value: "JUG", label: "Baloni" },
];

export default function NewRequisitionPage() {
    const navigate = useNavigate();
    const isCurrent = useRequisitionMutationScope();
    const { user, hasPermission } = useAuth();
    const [searchParams] = useSearchParams();
    const customerIdValue = Number(searchParams.get("customerId"));
    const customerId = Number.isInteger(customerIdValue) && customerIdValue > 0 ? customerIdValue : null;
    const { selectedCustomer, setSelectedCustomer, customerSearch, setCustomerSearch, matchingCustomers, isPreselected, loading: customerLoading, error: customerError } = useCustomerSelector(customerId);
    const [requisitionType, setRequisitionType] = useState<RequisitionType>("classic");
    const [gratisReason, setGratisReason] = useState<GratisReason>("");
    const [customReason, setCustomReason] = useState("");
    const [submitted, setSubmitted] = useState(false);
    const [productCategory, setProductCategory] = useState<ProductCategory>("ALL");
    const { products, loading: productsLoading, error: productsError } = useProducts(
        Boolean(user && hasPermission(PERMISSIONS.REQUISITIONS_CREATE)),
    );
    const visibleProductKeys = useMemo(() => new Set(
        products
            .filter((product) => productCategory === "ALL" || product.packageType === productCategory)
            .map((product) => product.key),
    ), [productCategory, products]);

    const resetFeedback = () => setSubmitted(false);

    const validateRequisitionDetails = () => {
        setSubmitted(true);

        const hasInvalidReason = requisitionType === "gratis" && !gratisReason;
        const hasInvalidCustomReason = requisitionType === "gratis"
            && gratisReason === "OTHER"
            && !customReason.trim();

        return Boolean(selectedCustomer) && !hasInvalidReason && !hasInvalidCustomReason;
    };

    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (!hasPermission(PERMISSIONS.REQUISITIONS_CREATE)) return <Navigate to={APP_ROUTES.requisitions} replace />;

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-5 sm:px-6 sm:pt-7 lg:px-8 lg:pb-12">
                <div className="mx-auto w-full max-w-5xl">
                    <button
                        type="button"
                        onClick={() => navigate(-1)}
                        className="inline-flex items-center gap-2 text-sm font-semibold text-yale-blue transition hover:text-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                    >
                        <ArrowLeft className="h-4 w-4" />
                        Nazad
                    </button>

                    <header className="mt-4 flex flex-col gap-2 border-b border-graphite/10 pb-5 sm:flex-row sm:items-end sm:justify-between">
                        <div>
                            <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Trebovanja</p>
                            <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">Novo trebovanje</h1>
                        </div>
                        {selectedCustomer && (
                            <p className="text-sm text-graphite/70">
                                Kupac: <strong className="text-graphite">{selectedCustomer.companyName}</strong>
                            </p>
                        )}
                    </header>

                    {productsLoading && <p className="mt-6 text-center text-graphite/60">Učitavanje proizvoda...</p>}
                    {productsError && <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-red-700">{productsError}</p>}
                    {!productsLoading && !productsError && <ProductRequestForm key={authApi.getToken()}
                        products={products}
                        submitLabel="Kreiraj trebovanje"
                        confirmationTitle="Potvrda trebovanja"
                        confirmationDescription="Proverite podatke pre kreiranja trebovanja."
                        quantitySteppers
                        visibleProductKeys={visibleProductKeys}
                        confirmationContext={selectedCustomer ? {
                            label: "Kupac",
                            primaryText: selectedCustomer.objectName,
                            secondaryText: selectedCustomer.companyName,
                        } : undefined}
                        validateBeforeConfirmation={validateRequisitionDetails}
                        onCancel={() => navigate(-1)}
                        onConfirm={async ({ products, note }) => {
                            if (!selectedCustomer) return;

                            await requisitionApi.create(requisitionPayload({
                                customerId: selectedCustomer.id, type: requisitionType, reason: gratisReason,
                                customReason, note, items: products.map(({ product, quantity }) => ({ productCode: product.code, quantity })),
                            }));
                            if (isCurrent()) navigate(APP_ROUTES.requisitions, { replace: true });
                        }}
                    >
                        {customerLoading && <p role="status">Učitavanje kupca...</p>}
                        {customerError && <p role="alert" className="text-red-700">{customerError}</p>}
                        <CustomerSelector
                            selectedCustomer={selectedCustomer}
                            matchingCustomers={matchingCustomers}
                            search={customerSearch}
                            isPreselected={isPreselected}
                            submitted={submitted}
                            onSearchChange={(value) => {
                                setCustomerSearch(value);
                                resetFeedback();
                            }}
                            onSelect={(customer) => {
                                setSelectedCustomer(customer);
                                setCustomerSearch("");
                                resetFeedback();
                            }}
                            onClear={() => {
                                setSelectedCustomer(null);
                                setCustomerSearch("");
                                resetFeedback();
                            }}
                        />

                        <RequisitionTypeSection
                            requisitionType={requisitionType}
                            gratisReason={gratisReason}
                            customReason={customReason}
                            submitted={submitted}
                            onTypeChange={(type) => {
                                setRequisitionType(type);
                                resetFeedback();
                            }}
                            onReasonChange={(reason) => {
                                setGratisReason(reason);
                                resetFeedback();
                            }}
                            onCustomReasonChange={(value) => {
                                setCustomReason(value);
                                resetFeedback();
                            }}
                        />

                        <section aria-label="Kategorija proizvoda" className="rounded-2xl border border-dust-grey bg-white p-4 shadow-sm">
                            <div className="flex flex-wrap gap-2">
                                {PRODUCT_CATEGORIES.map((category) => {
                                    const selected = productCategory === category.value;
                                    return (
                                        <button
                                            key={category.value}
                                            type="button"
                                            aria-pressed={selected}
                                            onClick={() => setProductCategory(category.value)}
                                            className={`rounded-xl px-4 py-2 text-sm font-bold transition focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 ${
                                                selected
                                                    ? "bg-yale-blue text-white shadow-sm"
                                                    : "border border-dust-grey bg-white text-graphite hover:border-stormy-teal hover:text-stormy-teal"
                                            }`}
                                        >
                                            {category.label}
                                        </button>
                                    );
                                })}
                            </div>
                        </section>
                    </ProductRequestForm>}
                </div>
            </main>
        </AppLayout>
    );
}
