import { useState } from "react";
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
import { requisitionApi, type CreateRequisitionRequest } from "../requisition_api";
import { useProducts } from "../../../shared/hooks/use_products";

export default function NewRequisitionPage() {
    const navigate = useNavigate();
    const { user, hasPermission } = useAuth();
    const [searchParams] = useSearchParams();
    const customerIdValue = Number(searchParams.get("customerId"));
    const customerId = Number.isInteger(customerIdValue) && customerIdValue > 0 ? customerIdValue : null;
    const { selectedCustomer, setSelectedCustomer, customerSearch, setCustomerSearch, matchingCustomers, isPreselected } = useCustomerSelector(customerId);
    const [requisitionType, setRequisitionType] = useState<RequisitionType>("classic");
    const [gratisReason, setGratisReason] = useState<GratisReason>("");
    const [customReason, setCustomReason] = useState("");
    const [submitted, setSubmitted] = useState(false);
    const { products, loading: productsLoading, error: productsError } = useProducts(
        Boolean(user && hasPermission(PERMISSIONS.REQUISITIONS_CREATE)),
    );
    const [submitError, setSubmitError] = useState("");
    const [submitting, setSubmitting] = useState(false);

    const resetFeedback = () => setSubmitted(false);

    const validateRequisitionDetails = () => {
        setSubmitted(true);

        const hasInvalidReason = requisitionType === "gratis" && !gratisReason;
        const hasInvalidCustomReason = requisitionType === "gratis"
            && gratisReason === "Drugo"
            && !customReason.trim();

        return Boolean(selectedCustomer) && !hasInvalidReason && !hasInvalidCustomReason;
    };

    if (!user) return <Navigate to="/login" replace />;
    if (!hasPermission(PERMISSIONS.REQUISITIONS_CREATE)) return <Navigate to="/trebovanja" replace />;

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
                    {!productsLoading && !productsError && <ProductRequestForm
                        products={products}
                        submitLabel="Kreiraj trebovanje"
                        confirmationTitle="Potvrda trebovanja"
                        confirmationDescription="Proverite podatke pre kreiranja trebovanja."
                        confirmationContext={selectedCustomer ? {
                            label: "Kupac",
                            primaryText: selectedCustomer.objectName,
                            secondaryText: selectedCustomer.companyName,
                        } : undefined}
                        validateBeforeConfirmation={validateRequisitionDetails}
                        onCancel={() => navigate(-1)}
                        onConfirm={async ({ products, note }) => {
                            if (!selectedCustomer) return;

                            const reasonMap = {
                                "Za ulistavanje": "LISTING",
                                "Za potrošnju": "CONSUMPTION",
                                Poklon: "GIFT",
                                Drugo: "OTHER",
                            } as const;
                            const payload: CreateRequisitionRequest = {
                                customerId: selectedCustomer.id,
                                type: requisitionType === "gratis" ? "GRATIS" : "NORMAL",
                                gratisReason: requisitionType === "gratis"
                                    ? reasonMap[gratisReason as Exclude<GratisReason, "">]
                                    : null,
                                customReason: requisitionType === "gratis" && gratisReason === "Drugo"
                                    ? customReason.trim()
                                    : null,
                                items: products.map(({ product, quantity }) => ({
                                    productCode: product.code,
                                    quantity,
                                })),
                                note,
                            };

                            setSubmitting(true);
                            setSubmitError("");
                            try {
                                await requisitionApi.create(payload);
                                navigate("/trebovanja", { replace: true });
                            } catch (requestError) {
                                setSubmitError(requestError instanceof Error ? requestError.message : "Trebovanje nije kreirano.");
                                throw requestError;
                            } finally {
                                setSubmitting(false);
                            }
                        }}
                    >
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
                    </ProductRequestForm>}
                    {submitting && <p role="status" className="mt-4 text-center text-sm font-semibold text-stormy-teal">Čuvanje trebovanja...</p>}
                    {submitError && <p role="alert" className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-red-700">{submitError}</p>}
                </div>
            </main>
        </AppLayout>
    );
}
