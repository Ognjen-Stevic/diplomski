import { AiRiskFilter } from "../../ai/components/ai_customer_filters";
import { useRiskBatch } from "../../ai/use_risk_batch";
import { RiskBadge } from "../../ai/risk_components";
import { useSessionRequest } from "../../../shared/hooks/use_session_request";
import { useCallback, useState, type FormEvent } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { CustomerSearch } from "../components/customer_search";
import { useAuth } from "../../auth/hooks/useAuth";
import { PERMISSIONS } from "../../../roles/permissions";
import { CustomerCard } from "../components/customer_card";
import { CustomerDetailsPanel } from "../components/customer_details_panel";
import Header from "../components/header";
import NoUserFound from "../components/no_user_found";
import { Pagination } from "../components/pagination";
import { customerApi } from "../customer_api";
import type { Customer, CustomerStatus } from "../types/customer.types";

export default function CustomersPage() {
    const navigate = useNavigate();
    const { user, hasPermission } = useAuth();
    const [searchParams, setSearchParams] = useSearchParams();
    const search = searchParams.get("search") ?? "";
    const risk = searchParams.get("risk") ?? "";
    const page = Math.max(1, Number(searchParams.get("page")) || 1);
    const [draftSearch, setDraftSearch] = useState({ search, value: search });
    const searchInput = draftSearch.search === search ? draftSearch.value : search;
    const setSearchInput = (value: string) => setDraftSearch({ search, value });
    const load = useCallback((signal: AbortSignal) => customerApi.findAll(search, page - 1, 50, signal, risk || undefined), [search, page, risk]);
    const query = useSessionRequest(`customers:${page}:${search}:${risk}`, load, hasPermission(PERMISSIONS.CUSTOMERS_READ));
    const linkedId = Number(searchParams.get("customerId"));
    const loadLinked = useCallback((signal: AbortSignal) => customerApi.findById(linkedId, signal), [linkedId]);
    const linked = useSessionRequest(`customer-link:${linkedId}`, loadLinked, Number.isInteger(linkedId) && linkedId > 0 && hasPermission(PERMISSIONS.CUSTOMERS_READ));
    const customers = query.data?.content ?? [];
    const risks = useRiskBatch(customers.map(customer => customer.id));
    const totalPages = query.data?.totalPages ?? 0;
    const { loading, error } = query;
    const [selection, setSelection] = useState<{ context: object; customer: Customer } | null>(null);
    const selectedCustomer = selection?.context === query.context ? customers.find(customer => customer.id === selection.customer.id) ?? null : customers.find(customer => customer.id === linkedId) ?? linked.data ?? null;
    const setSelectedCustomer = (customer: Customer | null) => { setSelection(customer ? { context: query.context, customer } : null); if(!customer && searchParams.has("customerId")){const next=new URLSearchParams(searchParams);next.delete("customerId");setSearchParams(next);}};

    if (!user) return <Navigate to="/login" replace />;
    if (!hasPermission(PERMISSIONS.CUSTOMERS_READ)) return <Navigate to="/" replace />;

    const submitSearch = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const next = new URLSearchParams(searchParams);
        next.delete("page"); next.delete("search");
        if (searchInput.trim()) next.set("search", searchInput.trim());
        setSearchParams(next);
    };

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <div className="w-full">
                    <Header />
                    {user.role !== "BOOKKEEPER" && <div className="mt-5 max-w-sm"><AiRiskFilter label="AI rizik" value={risk} onChange={(value) => {
                        const next = new URLSearchParams(searchParams);
                        next.delete("page");
                        if (value) next.set("risk", value); else next.delete("risk");
                        setSearchParams(next);
                    }} /></div>}

                    <div className="mt-7"><CustomerSearch value={searchInput} onChange={setSearchInput} onSubmit={submitSearch} /></div>
                    {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-red-700">{error}</p>}
                    {loading ? (
                        <p className="mt-8 text-center text-graphite/60">Učitavanje kupaca...</p>
                    ) : customers.length === 0 ? (
                        <div className="mt-8"><NoUserFound /></div>
                    ) : (
                        <div className="mt-8 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
                            {customers.map((customer) => (
                                <div key={customer.id}><RiskBadge level={risks.data?.find(a=>a.customerId===customer.id)?.level ?? "INSUFFICIENT_DATA"}/><CustomerCard
                                    customer={customer}
                                    onOpen={() => setSelectedCustomer(customer)}
                                    onOrder={() => navigate(`${APP_ROUTES.newRequisition}?customerId=${customer.id}`)}
                                    onRequisitionHistory={() => navigate(APP_ROUTES.customerRequisitions(customer.id))}
                                    onChangeSalesRep={() => setSelectedCustomer(customer)}
                                /></div>
                            ))}
                        </div>
                    )}
                    <Pagination currentPage={page} totalPages={totalPages} onChange={(nextPage) => {
                        const next = new URLSearchParams(searchParams);
                        next.set("page", String(nextPage));
                        setSearchParams(next);
                    }} />
                </div>
            </main>
            {selectedCustomer && <CustomerDetailsPanel
                customer={selectedCustomer}
                onClose={() => setSelectedCustomer(null)}
                onEdit={() => navigate(APP_ROUTES.editCustomer(selectedCustomer.id), { state: { customer: selectedCustomer } })}
                onStatusChange={async (status: CustomerStatus) => {
                    const updatedCustomer = await customerApi.updateStatus(selectedCustomer.id, status);
                    if (updatedCustomer.status === "deleted") {
                        query.reload(); linked.reload();
                        setSelectedCustomer(null);
                        return;
                    }
                    query.reload(); linked.reload();
                    setSelectedCustomer(updatedCustomer);
                }}
                onSalesRepChange={async (salesRepId: number) => {
                    const updatedCustomer = await customerApi.updateSalesRepresentative(
                        selectedCustomer.id,
                        salesRepId,
                    );
                    query.reload(); linked.reload();
                    setSelectedCustomer(updatedCustomer);
                }}
                onOrder={() => navigate(`${APP_ROUTES.newRequisition}?customerId=${selectedCustomer.id}`)}
                onRequisitionHistory={() => navigate(APP_ROUTES.customerRequisitions(selectedCustomer.id))}
                onPromotionalMaterial={() => navigate(
                    `${APP_ROUTES.newAdvertisingMaterialDistribution}?customerId=${selectedCustomer.id}`,
                )}
            />}
        </AppLayout>
    );
}
