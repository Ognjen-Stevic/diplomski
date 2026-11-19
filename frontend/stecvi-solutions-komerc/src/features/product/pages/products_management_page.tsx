import { useSessionCurrent } from "../../../shared/hooks/use_session_request";
import { SessionScope } from "../../../shared/components/session_scope";
import { useEffect, useRef, useState } from "react";
import { PackageSearch, Plus, Search } from "lucide-react";
import { Navigate, useSearchParams } from "react-router-dom";
import { APP_ROUTES } from "../../../app/routes";
import { useProductCatalog } from "../hooks/use_product_catalog";
import AppLayout from "../../../app/layouts/app_layout";
import { useAuth } from "../../auth/hooks/useAuth";
import { ProductDeactivationConfirmation } from "../components/product_deactivation_confirmation";
import { ProductForm } from "../components/product_form";
import { ProductManagementCard } from "../components/product_management_card";
import { Pagination } from "../../../shared/components/pagination";
import { productManagementApi } from "../product_management_api";
import type { ManagedProduct, ProductCreatePayload, ProductStatusFilter } from "../types/product_management.types";

export default function ProductsManagementPage() { return <SessionScope><ProductsManagementPageContent /></SessionScope>; }
function ProductsManagementPageContent() {
    const { user } = useAuth();
    const isCurrent = useSessionCurrent();
    const [searchParams, setSearchParams] = useSearchParams();
    const pageNumber = Math.max(1, Number(searchParams.get("page")) || 1);
    const [formError, setFormError] = useState("");
    const formOpen = searchParams.get("action") === "new";
    const savingRef = useRef(false);
    const deactivatingRef = useRef(false);
    const [deactivationError, setDeactivationError] = useState("");
    const [saving, setSaving] = useState(false);
    const [confirmation, setConfirmation] = useState<ManagedProduct | null>(null);
    const [deactivating, setDeactivating] = useState(false);
    const [search, setSearch] = useState("");
    const [status, setStatus] = useState<ProductStatusFilter>("ALL");
    const readOnly = user?.role === "BOOKKEEPER";
    const canViewProducts = user?.role === "CEO" || readOnly;

    const { page, products, loading, error: pageError, retry } = useProductCatalog(
        pageNumber - 1,
        search,
        status,
        canViewProducts,
    );

    useEffect(() => {
        if (!page || loading) return;
        const lastPage = Math.max(1, page.totalPages);
        if (pageNumber <= lastPage) return;
        setSearchParams((current) => {
            const next = new URLSearchParams(current);
            if (lastPage === 1) next.delete("page");
            else next.set("page", String(lastPage));
            return next;
        }, { replace: true });
    }, [loading, page, pageNumber, setSearchParams]);

    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (!canViewProducts) return <Navigate to={APP_ROUTES.home} replace />;

    const setFormOpen = (open: boolean) => {
        setSearchParams((current) => {
            const next = new URLSearchParams(current);
            if (open) next.set("action", "new");
            else next.delete("action");
            return next;
        }, { replace: true });
    };

    const createProduct = async (payload: ProductCreatePayload, image: File | null) => {
        if (user.role !== "CEO" || savingRef.current) return;
        savingRef.current = true;
        setSaving(true);
        setFormError("");
        try {
            await productManagementApi.create(payload, image);
            if (!isCurrent()) return;
            setFormOpen(false);
            retry();
        } catch (error) {
            setFormError(error instanceof Error ? error.message : "Artikal nije sačuvan.");
        } finally {
            savingRef.current = false;
            setSaving(false);
        }
    };

    const deactivateProduct = async () => {
        if (user.role !== "CEO" || !confirmation || deactivatingRef.current) return;
        deactivatingRef.current = true;
        setDeactivating(true);
        setDeactivationError("");
        try {
            await productManagementApi.deactivate(confirmation.id);
            if (!isCurrent()) return;
            setConfirmation(null);
            retry();
        } catch (error) {
            setDeactivationError(error instanceof Error ? error.message : "Artikal nije deaktiviran.");
        } finally {
            deactivatingRef.current = false;
            setDeactivating(false);
        }
    };

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">
                            {readOnly ? "Knjigovodstvo" : "CEO administracija"}
                        </p>
                        <h1 className="mt-1 text-3xl font-bold text-yale-blue">Artikli</h1>
                        <p className="mt-2 text-graphite/60">
                            {readOnly
                                ? "Pregled artikala, cena, količina i stimulacija."
                                : "Pregled artikala, stimulacija i trajna deaktivacija."}
                        </p>
                    </div>
                    {!readOnly && (
                        <button
                            onClick={() => { setFormError(""); setFormOpen(true); }}
                            className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-bold text-white hover:bg-stormy-teal"
                        >
                            <Plus className="h-5 w-5" />
                            Novi artikal
                        </button>
                    )}
                </div>

                <section className="mt-7 grid gap-3 rounded-2xl border border-dust-grey bg-white p-4 shadow-sm sm:grid-cols-[1fr_240px]">
                    <label className="relative">
                        <span className="sr-only">Pretraži artikle</span>
                        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-graphite/45" />
                        <input
                            type="search"
                            value={search}
                            onChange={(event) => {
                                setSearch(event.target.value);
                                setSearchParams((current) => {
                                    const next = new URLSearchParams(current);
                                    next.delete("page");
                                    return next;
                                }, { replace: true });
                            }}
                            placeholder="Naziv ili šifra"
                            className="w-full rounded-xl border border-dust-grey py-2.5 pl-10 pr-3 outline-none focus:border-stormy-teal"
                        />
                    </label>
                    <select
                        aria-label="Filtriraj po statusu"
                        value={status}
                        onChange={(event) => {
                            setStatus(event.target.value as ProductStatusFilter);
                            setSearchParams((current) => {
                                const next = new URLSearchParams(current);
                                next.delete("page");
                                return next;
                            }, { replace: true });
                        }}
                        className="rounded-xl border border-dust-grey px-3 py-2.5 outline-none focus:border-stormy-teal"
                    >
                        <option value="ALL">Svi statusi</option>
                        <option value="ACTIVE">Aktivni</option>
                        <option value="INACTIVE">Neaktivni</option>
                    </select>
                </section>

                {pageError && (
                    <div role="alert" className="mt-6 flex items-center justify-between gap-4 rounded-xl bg-red-50 px-4 py-3 text-red-700">
                        <span>{pageError}</span>
                        <button onClick={() => void retry()} className="shrink-0 font-bold hover:underline">
                            Pokušaj ponovo
                        </button>
                    </div>
                )}

                {loading ? (
                    <p className="mt-10 text-center text-graphite/60">Učitavanje artikala...</p>
                ) : products.length === 0 ? (
                    <div className="mt-10 flex flex-col items-center text-center text-graphite/55">
                        <PackageSearch className="h-10 w-10" />
                        <p className="mt-3 font-semibold">Nema pronađenih artikala.</p>
                    </div>
                ) : (
                    <>
                        <div className="mt-7 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
                            {products.map((product) => (
                                <ProductManagementCard
                                    key={product.id}
                                    product={product}
                                    deactivating={deactivating && confirmation?.id === product.id}
                                    onDeactivate={() => {
                                        if (deactivatingRef.current) return;
                                        setDeactivationError("");
                                        setConfirmation(product);
                                    }}
                                    readOnly={readOnly}
                                />
                            ))}
                        </div>
                        <Pagination
                            currentPage={pageNumber}
                            totalPages={page?.totalPages ?? 0}
                            onChange={(nextPage) => {
                                const next = new URLSearchParams(searchParams);
                                if (nextPage === 1) next.delete("page");
                                else next.set("page", String(nextPage));
                                setSearchParams(next);
                            }}
                        />
                    </>
                )}
            </main>

            {!readOnly && formOpen && (
                <ProductForm
                    saving={saving}
                    error={formError}
                    onCancel={() => { if (!savingRef.current) setFormOpen(false); }}
                    onSubmit={createProduct}
                />
            )}
            {!readOnly && confirmation && (
                <ProductDeactivationConfirmation
                    product={confirmation}
                    submitting={deactivating}
                    error={deactivationError}
                    onCancel={() => { if (!deactivatingRef.current) setConfirmation(null); }}
                    onConfirm={deactivateProduct}
                />
            )}
        </AppLayout>
    );
}
