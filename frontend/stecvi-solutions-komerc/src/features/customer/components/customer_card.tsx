import { Building2, ChevronRight, ClipboardList, FileClock, MapPin, Phone, UserRoundCog } from "lucide-react";
import type { MouseEvent } from "react";
import { PERMISSIONS } from "../../../roles/permissions";
import { useAuth } from "../../auth/hooks/useAuth";
import type { Customer } from "../types/customer.types";

function stopCardClick(event: MouseEvent<HTMLElement>) {
    event.stopPropagation();
}

function getCallablePhone(phone: string): string | null {
    const trimmedPhone = phone.trim();
    const isValid = /^\+?[\d\s()/-]{6,25}$/.test(trimmedPhone)
        && trimmedPhone.replace(/\D/g, "").length >= 6;

    return isValid ? trimmedPhone.replace(/\s/g, "") : null;
}

export function CustomerCard({
    customer,
    onOpen,
    onOrder,
    onRequisitionHistory,
    onChangeSalesRep,
}: {
    customer: Customer;
    onOpen: () => void;
    onOrder: () => void;
    onRequisitionHistory: () => void;
    onChangeSalesRep: () => void;
}) {
    const { user, hasPermission } = useAuth();
    const isSalesRepresentative = user?.role === "SALES_REP";
    const canShowCallAction = user?.role !== "MANAGER";
    const canUsePrimaryAction = isSalesRepresentative
        ? customer.actions.canCreateRequisition && hasPermission(PERMISSIONS.REQUISITIONS_CREATE)
        : customer.actions.canViewRequisitionHistory && hasPermission(PERMISSIONS.REQUISITIONS_READ);
    const callablePhone = getCallablePhone(customer.phone);
    const canCall = callablePhone !== null && hasPermission(PERMISSIONS.CUSTOMERS_CALL);
    const primaryUnavailableId = `customer-${customer.id}-primary-unavailable`;
    const callUnavailableId = `customer-${customer.id}-call-unavailable`;
    const actionClassName = "inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-3 py-2.5 text-sm font-semibold text-white transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2 aria-disabled:cursor-not-allowed aria-disabled:bg-graphite/20 aria-disabled:text-graphite/55 aria-disabled:hover:bg-graphite/20";

    return (
        <article className="group relative flex h-full flex-col overflow-hidden rounded-2xl border border-dust-grey bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-stormy-teal hover:shadow-md">
            <button
                type="button"
                onClick={onOpen}
                className="absolute inset-0 z-0 rounded-2xl focus:outline-none focus:ring-2 focus:ring-inset focus:ring-stormy-teal/40"
                aria-label={`Otvori detalje kupca ${customer.objectName}`}
            />

            <div className="pointer-events-none relative z-10 flex h-full flex-col">
                <div className="flex items-start gap-4 pb-5">
                    <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-dust-grey/35 text-yale-blue">
                        <Building2 className="h-5 w-5" aria-hidden="true" />
                    </span>

                    <div className="min-w-0 flex-1">
                        <h2 className="truncate text-lg font-bold text-yale-blue">{customer.objectName}</h2>
                        <p className="mt-2 flex items-start gap-2 text-sm leading-5 text-graphite/65">
                            <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-stormy-teal" />
                            <span>{customer.address}</span>
                        </p>
                        {customer.salesRep && (
                            <p className="mt-2 text-sm text-graphite/60">
                                Komercijalista: <strong className="text-graphite">
                                    {customer.salesRep.firstName} {customer.salesRep.lastName}
                                </strong>
                            </p>
                        )}
                        {customer.status === "draft" && (
                            <p className="mt-2 text-sm font-semibold text-amber-700">
                                Čeka se dozvola menadžera
                            </p>
                        )}
                        {customer.status === "deactivated" && (
                            <p className="mt-2 text-sm font-semibold text-red-700">
                                Kupac je deaktiviran
                            </p>
                        )}
                    </div>

                    <ChevronRight className="mt-1 h-5 w-5 shrink-0 text-graphite/30 transition group-hover:translate-x-1 group-hover:text-stormy-teal" />
                </div>


                <div className="pointer-events-auto relative z-20 mt-auto grid grid-cols-2 gap-3 border-t border-dust-grey pt-4">
                    <button
                        type="button"
                        aria-disabled={!canUsePrimaryAction}
                        aria-describedby={!canUsePrimaryAction ? primaryUnavailableId : undefined}
                        onClick={(event) => {
                            stopCardClick(event);
                            if (!canUsePrimaryAction) return;
                            if (isSalesRepresentative) onOrder();
                            else onRequisitionHistory();
                        }}
                        className={actionClassName}
                    >
                        {isSalesRepresentative
                            ? <ClipboardList className="h-4 w-4" aria-hidden="true" />
                            : <FileClock className="h-4 w-4" aria-hidden="true" />}
                        {isSalesRepresentative ? "Novo trebovanje" : "Istorija trebovanja"}
                        {!canUsePrimaryAction && (
                            <span id={primaryUnavailableId} className="sr-only">
                                Ova akcija nije dostupna za izabranog kupca.
                            </span>
                        )}
                    </button>

                    {canShowCallAction && (canCall ? (
                        <a
                            href={`tel:${callablePhone}`}
                            onClick={stopCardClick}
                            className={actionClassName}
                        >
                            <Phone className="h-4 w-4" aria-hidden="true" />
                            Pozovi
                        </a>
                    ) : (
                        <button
                            type="button"
                            aria-disabled="true"
                            aria-describedby={callUnavailableId}
                            onClick={stopCardClick}
                            className={actionClassName}
                        >
                            <Phone className="h-4 w-4" aria-hidden="true" />
                            Pozovi
                            <span id={callUnavailableId} className="sr-only">
                                Pozivanje nije dostupno jer broj telefona nije ispravan ili nemate dozvolu.
                            </span>
                        </button>
                    ))}

                    {customer.actions.canChangeSalesRep && (
                        <button
                            type="button"
                            onClick={(event) => {
                                stopCardClick(event);
                                onChangeSalesRep();
                            }}
                            className="inline-flex items-center justify-center gap-2 rounded-xl bg-yale-blue px-3 py-2.5 text-sm font-semibold text-white transition hover:bg-stormy-teal focus:outline-none focus:ring-2 focus:ring-stormy-teal focus:ring-offset-2"
                        >
                            <UserRoundCog className="h-4 w-4" aria-hidden="true" />
                            Promeni komercijalistu
                        </button>
                    )}
                </div>
            </div>
        </article>
    );
}
