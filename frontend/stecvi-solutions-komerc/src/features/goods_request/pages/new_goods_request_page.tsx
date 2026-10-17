import { useMemo, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { Navigate, useNavigate } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { ProductRequestForm } from "../../../shared/components/product_request/product_request_form";
import { useAuth } from "../../auth/hooks/useAuth";
import { canCreateGoodsRequest } from "../../../roles/access_control";
import { useProducts } from "../../../shared/hooks/use_products";
import { useAvailableAdvertisingMaterials } from "../../advertising_materials/hooks/use_available_advertising_materials";
import type { RequestCatalogItem } from "../../../shared/types/request_catalog_item";
import type { Product } from "../../../shared/types/product";
import { goodsRequestApi } from "../goods_request_api";

type RequestKind = "products" | "advertising-materials";
type AdvertisingMaterialRequestItem = RequestCatalogItem & { materialId: number };
type GoodsRequestCatalogItem = Product | AdvertisingMaterialRequestItem;

export default function NewGoodsRequestPage() {
    const navigate = useNavigate();
    const { user, hasPermission } = useAuth();
    const [requestKind, setRequestKind] = useState<RequestKind>("products");
    const canCreate = canCreateGoodsRequest(user?.role, hasPermission);
    const { products, loading: productsLoading, error: productsError } = useProducts(
        canCreate && requestKind === "products",
    );
    const {
        materials: advertisingMaterials,
        loading: advertisingMaterialsLoading,
        error: advertisingMaterialsError,
    } = useAvailableAdvertisingMaterials(
        canCreate && requestKind === "advertising-materials",
    );
    const advertisingMaterialItems = useMemo<AdvertisingMaterialRequestItem[]>(() =>
        advertisingMaterials.map((material) => ({
            key: `advertising-material-${material.id}`,
            materialId: material.id,
            name: material.name,
        })), [advertisingMaterials]);
    const isProductRequest = requestKind === "products";
    const catalogItems: readonly GoodsRequestCatalogItem[] = isProductRequest
        ? products
        : advertisingMaterialItems;
    const catalogLoading = isProductRequest ? productsLoading : advertisingMaterialsLoading;
    const catalogError = isProductRequest ? productsError : advertisingMaterialsError;

    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (!canCreate) return <Navigate to={APP_ROUTES.goodsRequests} replace />;

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

                    <header className="mt-4 border-b border-graphite/10 pb-5">
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">Magacin</p>
                        <h1 className="mt-1 text-3xl font-bold tracking-tight text-yale-blue sm:text-4xl">
                            Novi zahtev
                        </h1>

                        <div className="mt-5 flex flex-col gap-2 sm:flex-row" role="group" aria-label="Vrsta zahteva">
                            <button
                                type="button"
                                aria-pressed={isProductRequest}
                                onClick={() => setRequestKind("products")}
                                className={`rounded-xl border px-4 py-2.5 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 ${
                                    isProductRequest
                                        ? "border-yale-blue bg-yale-blue text-white shadow-sm"
                                        : "border-dust-grey bg-white text-graphite hover:border-stormy-teal hover:text-stormy-teal"
                                }`}
                            >
                                Zahtev za robu
                            </button>
                            <button
                                type="button"
                                aria-pressed={!isProductRequest}
                                onClick={() => setRequestKind("advertising-materials")}
                                className={`rounded-xl border px-4 py-2.5 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-stormy-teal/30 ${
                                    !isProductRequest
                                        ? "border-yale-blue bg-yale-blue text-white shadow-sm"
                                        : "border-dust-grey bg-white text-graphite hover:border-stormy-teal hover:text-stormy-teal"
                                }`}
                            >
                                Zahtev za reklamni materijal
                            </button>
                        </div>
                    </header>

                    {catalogLoading && (
                        <p className="mt-6 text-center text-graphite/60">
                            {isProductRequest ? "Učitavanje proizvoda..." : "Učitavanje reklamnog materijala..."}
                        </p>
                    )}
                    {catalogError && (
                        <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-red-700">{catalogError}</p>
                    )}
                    {!catalogLoading && !catalogError && (
                        <ProductRequestForm<GoodsRequestCatalogItem>
                            key={requestKind}
                            products={catalogItems}
                            itemsTitle={isProductRequest ? "Proizvodi" : "Reklamni materijal"}
                            itemsDescription={isProductRequest
                                ? "Unesite potrebne količine proizvoda."
                                : "Unesite potrebne količine reklamnog materijala."}
                            selectedItemsTitle={isProductRequest ? "Izabrani proizvodi" : "Izabrani reklamni materijal"}
                            submitLabel="Kreiraj zahtev"
                            confirmationTitle={isProductRequest
                                ? "Potvrda zahteva za robu"
                                : "Potvrda zahteva za reklamni materijal"}
                            confirmationDescription="Proverite izabrane stavke i količine pre kreiranja zahteva."
                            onCancel={() => navigate(-1)}
                            onConfirm={async ({ products: selectedProducts, note }) => {
                                await goodsRequestApi.create({
                                    items: selectedProducts.map(({ product, quantity }) =>
                                        "materialId" in product
                                            ? { materialId: product.materialId, quantity }
                                            : { productCode: product.code, quantity },
                                    ),
                                    note,
                                });
                                navigate(APP_ROUTES.goodsRequests);
                            }}
                        />
                    )}
                </div>
            </main>
        </AppLayout>
    );
}
