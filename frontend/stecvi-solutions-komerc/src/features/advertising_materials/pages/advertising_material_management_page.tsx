import { useSessionCurrent } from "../../../shared/hooks/use_session_request";
import { SessionScope } from "../../../shared/components/session_scope";
import { useMemo, useState } from "react";
import { Package, Plus, Search } from "lucide-react";
import { Navigate, useSearchParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { useAuth } from "../../auth/hooks/useAuth";
import { AdvertisingMaterialForm } from "../components/advertising_material_form";
import { AdvertisingMaterialManagementCard } from "../components/advertising_material_management_card";
import { AdvertisingMaterialStatusConfirmation } from "../components/advertising_material_status_confirmation";
import { useAdvertisingMaterialCatalog } from "../hooks/use_advertising_material_catalog";
import type { AdvertisingMaterial } from "../types/advertising_material.types";

type StatusFilter = "ALL" | "ACTIVE" | "INACTIVE";

export default function AdvertisingMaterialManagementPage() { return <SessionScope><AdvertisingMaterialManagementPageContent /></SessionScope>; }
function AdvertisingMaterialManagementPageContent() {
    const { user } = useAuth();
    const isCurrent = useSessionCurrent();
    const [searchParams, setSearchParams] = useSearchParams();
    const catalog = useAdvertisingMaterialCatalog(user?.role === "CEO");
    const [formError, setFormError] = useState("");
    const [operationError, setOperationError] = useState("");
    const [saving, setSaving] = useState(false);
    const [confirmation, setConfirmation] = useState<AdvertisingMaterial | null>(null);
    const [changingStatus, setChangingStatus] = useState(false);
    const [search, setSearch] = useState("");
    const [status, setStatus] = useState<StatusFilter>("ALL");
    const formOpen = searchParams.get("action") === "new";

    const filteredMaterials = useMemo(() => {
        const term = search.trim().toLocaleLowerCase("sr-Latn-RS");
        return catalog.materials.filter((material) => {
            const matchesSearch = !term
                || material.name.toLocaleLowerCase("sr-Latn-RS").includes(term);
            const matchesStatus = status === "ALL"
                || material.active === (status === "ACTIVE");
            return matchesSearch && matchesStatus;
        });
    }, [catalog.materials, search, status]);

    if (!user) return <Navigate to={APP_ROUTES.login} replace />;
    if (user.role !== "CEO") return <Navigate to={APP_ROUTES.home} replace />;

    const setFormVisibility = (open: boolean) => {
        setSearchParams((current) => {
            const next = new URLSearchParams(current);
            if (open) next.set("action", "new");
            else next.delete("action");
            return next;
        }, { replace: true });
    };

    const createMaterial = async (name: string) => {
        if (saving) return;
        setSaving(true);
        setFormError("");
        try {
            await catalog.createAdvertisingMaterial(name);
            if (isCurrent()) setFormVisibility(false);
        } catch (error) {
            setFormError(
                error instanceof Error
                    ? error.message
                    : "Reklamni materijal nije sačuvan.",
            );
        } finally {
            setSaving(false);
        }
    };

    const changeStatus = async () => {
        if (!confirmation || changingStatus) return;
        setChangingStatus(true);
        setOperationError("");
        try {
            await catalog.setAdvertisingMaterialActive(
                confirmation,
                !confirmation.active,
            );
            setConfirmation(null);
        } catch (error) {
            setOperationError(
                error instanceof Error
                    ? error.message
                    : "Status reklamnog materijala nije promenjen.",
            );
        } finally {
            setChangingStatus(false);
        }
    };

    const pageError = operationError || catalog.error;

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">CEO administracija</p>
                        <h1 className="mt-1 text-3xl font-bold text-yale-blue">Reklamni materijal</h1>
                        <p className="mt-2 text-graphite/60">Kreiranje, aktiviranje i deaktiviranje reklamnog materijala.</p>
                    </div>
                    <button
                        type="button"
                        onClick={() => {
                            setFormError("");
                            setFormVisibility(true);
                        }}
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-5 py-3 font-bold text-white hover:bg-stormy-teal"
                    >
                        <Plus className="h-5 w-5" />
                        Novi reklamni materijal
                    </button>
                </div>

                <section className="mt-7 grid gap-3 rounded-2xl border border-dust-grey bg-white p-4 shadow-sm sm:grid-cols-[1fr_240px]">
                    <label className="relative">
                        <span className="sr-only">Pretraži reklamni materijal</span>
                        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-graphite/45" />
                        <input
                            type="search"
                            value={search}
                            onChange={(event) => setSearch(event.target.value)}
                            placeholder="Naziv"
                            className="w-full rounded-xl border border-dust-grey py-2.5 pl-10 pr-3 outline-none focus:border-stormy-teal"
                        />
                    </label>
                    <select
                        aria-label="Filtriraj po statusu"
                        value={status}
                        onChange={(event) => setStatus(event.target.value as StatusFilter)}
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
                        <button
                            type="button"
                            onClick={() => {
                                setOperationError("");
                                catalog.clearError();
                                void catalog.reload();
                            }}
                            className="shrink-0 font-bold hover:underline"
                        >
                            Pokušaj ponovo
                        </button>
                    </div>
                )}

                {catalog.loading ? (
                    <p className="mt-10 text-center text-graphite/60">Učitavanje reklamnog materijala...</p>
                ) : filteredMaterials.length === 0 ? (
                    <div className="mt-10 flex flex-col items-center text-center text-graphite/55">
                        <Package className="h-10 w-10" />
                        <p className="mt-3 font-semibold">Nema pronađenog reklamnog materijala.</p>
                    </div>
                ) : (
                    <div className="mt-7 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
                        {filteredMaterials.map((material) => (
                            <AdvertisingMaterialManagementCard
                                key={material.id}
                                material={material}
                                changingStatus={changingStatus && confirmation?.id === material.id}
                                onChangeStatus={() => setConfirmation(material)}
                            />
                        ))}
                    </div>
                )}
            </main>

            {formOpen && (
                <AdvertisingMaterialForm
                    saving={saving}
                    error={formError}
                    onCancel={() => {
                        if (!saving) setFormVisibility(false);
                    }}
                    onSubmit={createMaterial}
                />
            )}
            {confirmation && (
                <AdvertisingMaterialStatusConfirmation
                    material={confirmation}
                    submitting={changingStatus}
                    onCancel={() => {
                        if (!changingStatus) setConfirmation(null);
                    }}
                    onConfirm={() => void changeStatus()}
                />
            )}
        </AppLayout>
    );
}
