import { useMemo, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { canAssignAdvertisingMaterial } from "../../../roles/access_control";
import { CustomerSelector } from "../../../shared/components/customer_selector";
import { ProductRequestForm } from "../../../shared/components/product_request/product_request_form";
import type { RequestCatalogItem } from "../../../shared/types/request_catalog_item";
import { useAuth } from "../../auth/hooks/useAuth";
import { useCustomerSelector } from "../../customer/use_customer_selector";
import { advertisingMaterialDistributionApi } from "../api/advertising_material_distribution_api";
import { useAvailableAdvertisingMaterials } from "../hooks/use_available_advertising_materials";

type AdvertisingMaterialRequestItem = RequestCatalogItem & {
    materialId: number;
};

export default function NewAdvertisingMaterialDistributionPage() {
    const navigate = useNavigate();
    const { user, hasPermission } = useAuth();
    const canCreate = canAssignAdvertisingMaterial(user?.role, hasPermission);
    const availableMaterials = useAvailableAdvertisingMaterials(canCreate);
    const materials = useMemo<AdvertisingMaterialRequestItem[]>(() =>
        availableMaterials.materials.map((material) => ({
            key: `advertising-material-${material.id}`,
            materialId: material.id,
            name: material.name,
        })), [availableMaterials.materials]);
    const [searchParams] = useSearchParams();
    const customerIdValue = Number(searchParams.get("customerId"));
    const customerId = Number.isSafeInteger(customerIdValue) && customerIdValue > 0
        ? customerIdValue
        : null;
    const customerSelector = useCustomerSelector(customerId);
    const [submitted, setSubmitted] = useState(false);

    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (!canCreate) return <Navigate to={APP_ROUTES.advertisingMaterials} replace />;

    const resetFeedback = () => setSubmitted(false);

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-5 sm:px-6 sm:pt-7 lg:px-8 lg:pb-12">
                <div className="mx-auto w-full max-w-5xl">
                    <button
                        type="button"
                        onClick={() => navigate(-1)}
                        className="inline-flex items-center gap-2 text-sm font-semibold text-yale-blue transition hover:text-stormy-teal"
                    >
                        <ArrowLeft className="h-4 w-4" />
                        Nazad
                    </button>

                    <header className="mt-4 flex flex-col gap-2 border-b border-graphite/10 pb-5 sm:flex-row sm:items-end sm:justify-between">
                        <div>
                            <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Podrška prodaji</p>
                            <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">Dodela reklamnog materijala</h1>
                        </div>
                        {customerSelector.selectedCustomer && (
                            <p className="text-sm text-graphite/70">
                                Kupac: <strong className="text-graphite">{customerSelector.selectedCustomer.companyName}</strong>
                            </p>
                        )}
                    </header>

                    {availableMaterials.loading && (
                        <p className="mt-6 text-center text-graphite/60">Učitavanje reklamnog materijala...</p>
                    )}
                    {availableMaterials.error && (
                        <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-red-700">
                            {availableMaterials.error}
                        </p>
                    )}

                    {!availableMaterials.loading && !availableMaterials.error && (
                        <ProductRequestForm
                            products={materials}
                            itemsTitle="Reklamni materijal"
                            itemsDescription="Unesite količine materijala koji se predaje kupcu."
                            selectedItemsTitle="Izabrani reklamni materijal"
                            submitLabel="Evidentiraj dodelu"
                            confirmationTitle="Potvrda dodele materijala"
                            confirmationDescription="Proverite kupca, materijal i količine pre potvrde."
                            confirmationContext={customerSelector.selectedCustomer ? {
                                label: "Kupac",
                                primaryText: customerSelector.selectedCustomer.objectName,
                                secondaryText: customerSelector.selectedCustomer.companyName,
                            } : undefined}
                            validateBeforeConfirmation={() => {
                                setSubmitted(true);
                                return Boolean(customerSelector.selectedCustomer);
                            }}
                            onCancel={() => navigate(-1)}
                            onConfirm={async ({ products, note }) => {
                                if (!customerSelector.selectedCustomer) {
                                    throw new Error("Izaberite kupca.");
                                }
                                await advertisingMaterialDistributionApi
                                    .recordAdvertisingMaterialDistribution({
                                        customerId: customerSelector.selectedCustomer.id,
                                        items: products.map(({ product, quantity }) => ({
                                            materialId: product.materialId,
                                            quantity,
                                        })),
                                        note,
                                    });
                                navigate(APP_ROUTES.advertisingMaterials, { replace: true });
                            }}
                        >
                            <CustomerSelector
                                selectedCustomer={customerSelector.selectedCustomer}
                                matchingCustomers={customerSelector.matchingCustomers}
                                search={customerSelector.customerSearch}
                                isPreselected={customerSelector.isPreselected}
                                submitted={submitted}
                                onSearchChange={(value) => {
                                    customerSelector.setCustomerSearch(value);
                                    resetFeedback();
                                }}
                                onSelect={(customer) => {
                                    customerSelector.setSelectedCustomer(customer);
                                    customerSelector.setCustomerSearch("");
                                    resetFeedback();
                                }}
                                onClear={() => {
                                    customerSelector.setSelectedCustomer(null);
                                    customerSelector.setCustomerSearch("");
                                    resetFeedback();
                                }}
                            />
                        </ProductRequestForm>
                    )}
                </div>
            </main>
        </AppLayout>
    );
}
