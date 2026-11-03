import { CustomerAiAnalysis } from "../../ai/risk_components";
import { ClipboardList, FileClock, FilePenLine, MapPin, Package, Phone, X } from "lucide-react";
import { DetailRow } from "./detail_row";
import { useEffect } from "react";
import type { Customer, CustomerStatus } from "../types/customer.types";
import { useAuth } from "../../auth/hooks/useAuth";
import { canAssignAdvertisingMaterial } from "../../../roles/access_control";
import { PERMISSIONS } from "../../../roles/permissions";
import { CustomerStatusControl } from "./customer_status_control";
import { CustomerSalesRepControl } from "./customer_sales_rep_control";

export function CustomerDetailsPanel({
    customer,
    onClose,
    onEdit,
    onStatusChange,
    onSalesRepChange,
    onOrder,
    onRequisitionHistory,
    onPromotionalMaterial,
}: {
    customer: Customer;
    onClose: () => void;
    onEdit: () => void;
    onStatusChange: (status: CustomerStatus) => Promise<void>;
    onSalesRepChange: (salesRepId: number) => Promise<void>;
    onOrder: () => void;
    onRequisitionHistory: () => void;
    onPromotionalMaterial: () => void;
}) {
    const { user, hasPermission } = useAuth();
    const canAssignMaterial = canAssignAdvertisingMaterial(user?.role, hasPermission);
    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") onClose();
        };

        document.addEventListener("keydown", handleKeyDown);
        document.body.style.overflow = "hidden";

        return () => {
            document.removeEventListener("keydown", handleKeyDown);
            document.body.style.overflow = "";
        };
    }, [onClose]);

    return (
        <div
            className="fixed inset-0 z-50 flex justify-end bg-graphite/45"
            role="dialog"
            aria-modal="true"
            aria-labelledby="customer-details-title"
        >
            <button
                type="button"
                aria-label="Zatvori detalje kupca"
                onClick={onClose}
                className="absolute inset-0 cursor-default"
            />

            <section className="relative z-10 flex h-full w-full max-w-xl flex-col overflow-hidden bg-white shadow-2xl">
                <header className="flex items-start justify-between gap-4 border-b border-dust-grey px-5 py-5 sm:px-7">
                    <div className="min-w-0">
                        <p className="text-xs font-bold uppercase tracking-[0.16em] text-stormy-teal">
                            Detalji kupca
                        </p>
                        <h2
                            id="customer-details-title"
                            className="mt-1 truncate text-2xl font-bold text-yale-blue"
                        >
                            {customer.objectName}
                        </h2>
                        <p className="mt-1 truncate text-sm text-graphite/60">
                            {customer.companyName}
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={onClose}
                        className="rounded-xl p-2 text-graphite transition hover:bg-dust-grey/35 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                        aria-label="Zatvori"
                    >
                        <X className="h-5 w-5" />
                    </button>
                </header>

                <div className="flex-1 overflow-y-auto px-5 py-6 sm:px-7">
                    <section>
                            {customer.phone && (
                            <a
                                href={`tel:${customer.phone.replace(/\s/g, "")}`}
                                className="mt-3 inline-flex w-full gap-2 items-center justify-center gap-2 rounded-xl border border-yale-blue px-4 py-3 text-sm font-semibold text-yale-blue transition hover:bg-yale-blue/5 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                            >
                                <Phone className="h-4 w-4" />
                                Pozovi
                            </a>
                            )}
                            {customer.googleMapsUrl && <a
                                href={customer.googleMapsUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="mt-3 inline-flex w-full gap-2 items-center justify-center gap-2 rounded-xl border border-yale-blue px-4 py-3 text-sm font-semibold text-yale-blue transition hover:bg-yale-blue/5 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                            >
                                <MapPin className="h-4 w-4" />
                                Otvori mapu
                            </a>}
                            {customer.actions.canCreateRequisition && (
                            <button
                                type="button"
                                onClick={onOrder}
                                className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-yale-blue px-4 py-3 font-semibold text-white transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2"
                            >
                                <ClipboardList className="h-5 w-5" />
                                Novo trebovanje
                            </button>
                            )}
                            {customer.actions.canViewRequisitionHistory && (
                            <button
                                type="button"
                                onClick={onRequisitionHistory}
                                className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-yale-blue px-4 py-3 font-semibold text-white transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2"
                            >
                                <FileClock className="h-5 w-5" />
                                Istorija trebovanja
                            </button>
                            )}
                    </section>
                    <section className="mt-7">
                        <h3 className="text-base font-bold text-yale-blue">
                            Podaci o kupcu
                        </h3>
                        <dl className="mt-3 rounded-2xl border border-dust-grey px-4">
                            <CustomerAiAnalysis key={customer.id} customerId={customer.id}/>
                        <DetailRow label="Naziv objekta" value={customer.objectName} />
                            <DetailRow label="Naziv firme" value={customer.companyName} />
                            <DetailRow label="PIB" value={customer.pib} />
                            <DetailRow label="Broj telefona" value={customer.phone} />
                            <DetailRow label="E-mail" value={customer.email} />
                            <DetailRow label="Adresa" value={customer.address} />
                            <DetailRow label="Napomena" value={customer.note} />
                        </dl>
                    </section>

                    {(hasPermission(PERMISSIONS.CUSTOMERS_UPDATE) || canAssignMaterial) && (
                    <section className="mt-7">
                            <h3 className="text-base font-bold text-yale-blue">
                                Ostale akcije
                            </h3>
                            <div className="mt-3 grid gap-3 sm:grid-cols-2">
                                {hasPermission(PERMISSIONS.CUSTOMERS_UPDATE) && <button
                                    type="button"
                                    onClick={onEdit}
                                    className="inline-flex items-center justify-center gap-2 rounded-xl border border-dust-grey px-4 py-3 text-sm font-semibold text-yale-blue transition hover:border-stormy-teal hover:bg-dust-grey/20 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                                >
                                    <FilePenLine className="h-4 w-4" />
                                    Izmeni kupca
                                </button>}

                                {canAssignMaterial && <button
                                    type="button"
                                    onClick={onPromotionalMaterial}
                                    className="inline-flex items-center justify-center gap-2 rounded-xl border border-dust-grey px-4 py-3 text-sm font-semibold text-yale-blue transition hover:border-stormy-teal hover:bg-dust-grey/20 focus:outline-none focus:ring-2 focus:ring-stormy-teal/30"
                                >
                                    <Package className="h-4 w-4" />
                                    Reklamni materijal
                                </button>}
                            </div>
                    </section>
                    )}
                </div>

                {customer.actions.canChangeSalesRep && customer.salesRep && (
                    <CustomerSalesRepControl
                        key={`${customer.id}-${customer.salesRep.id}`}
                        currentSalesRep={customer.salesRep}
                        onSalesRepChange={onSalesRepChange}
                    />
                )}
                {user?.role === "MANAGER" && (
                    <CustomerStatusControl
                        key={`${customer.id}-${customer.status}`}
                        customerName={customer.objectName}
                        currentStatus={customer.status}
                        onStatusChange={onStatusChange}
                    />
                )}
            </section >
        </div >
    );
}
