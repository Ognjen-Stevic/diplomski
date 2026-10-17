import { useEffect, useState, type FormEvent } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";

import AppLayout from "../../../app/layouts/app_layout";
import { APP_ROUTES } from "../../../app/routes";
import { CustomerSearch } from "../../dashboard/components/customer_search";
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
    const page = Math.max(1, Number(searchParams.get("page")) || 1);
    const [searchInput, setSearchInput] = useState(search);
    const [customers, setCustomers] = useState<Customer[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => setSearchInput(search), [search]);

    useEffect(() => {
        if (!user || !hasPermission(PERMISSIONS.CUSTOMERS_READ)) return;
        let active = true;
        setLoading(true);
        setError("");
        customerApi.findAll(search, page - 1, 50)
            .then((result) => {
                if (!active) return;
                setCustomers(result.content);
                setTotalPages(result.totalPages);
            })
            .catch((requestError: unknown) => {
                if (active) setError(requestError instanceof Error ? requestError.message : "Kupci nisu učitani.");
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [hasPermission, page, search, user]);

    if (!user) return <Navigate to="/login" replace />;
    if (!hasPermission(PERMISSIONS.CUSTOMERS_READ)) return <Navigate to="/" replace />;

    const submitSearch = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const next = new URLSearchParams();
        if (searchInput.trim()) next.set("search", searchInput.trim());
        setSearchParams(next);
    };

    return (
        <AppLayout>
            <main className="w-full min-w-0 px-4 pb-28 pt-6 sm:px-6 sm:pt-8 lg:px-10 lg:pb-12 xl:px-12">
                <div className="w-full">
                    <Header />
                    <div className="mt-7"><CustomerSearch value={searchInput} onChange={setSearchInput} onSubmit={submitSearch} /></div>
                    {error && <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-red-700">{error}</p>}
                    {loading ? (
                        <p className="mt-8 text-center text-graphite/60">Učitavanje kupaca...</p>
                    ) : customers.length === 0 ? (
                        <div className="mt-8"><NoUserFound /></div>
                    ) : (
                        <div className="mt-8 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
                            {customers.map((customer) => (
                                <CustomerCard
                                    key={customer.id}
                                    customer={customer}
                                    onOpen={() => setSelectedCustomer(customer)}
                                    onOrder={() => navigate(`${APP_ROUTES.newRequisition}?customerId=${customer.id}`)}
                                    onRequisitionHistory={() => navigate(`${APP_ROUTES.requisitions}/kupac/${customer.id}`)}
                                    onChangeSalesRep={() => setSelectedCustomer(customer)}
                                />
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
                onEdit={() => navigate(`/kupci/${selectedCustomer.id}/izmena`, { state: { customer: selectedCustomer } })}
                onStatusChange={async (status: CustomerStatus) => {
                    const updatedCustomer = await customerApi.updateStatus(selectedCustomer.id, status);
                    if (updatedCustomer.status === "deleted") {
                        setCustomers((current) => current.filter((customer) => customer.id !== updatedCustomer.id));
                        setSelectedCustomer(null);
                        return;
                    }
                    setCustomers((current) => current.map((customer) =>
                        customer.id === updatedCustomer.id ? updatedCustomer : customer));
                    setSelectedCustomer(updatedCustomer);
                }}
                onSalesRepChange={async (salesRepId: number) => {
                    const updatedCustomer = await customerApi.updateSalesRepresentative(
                        selectedCustomer.id,
                        salesRepId,
                    );
                    setCustomers((current) => current.map((customer) =>
                        customer.id === updatedCustomer.id ? updatedCustomer : customer));
                    setSelectedCustomer(updatedCustomer);
                }}
                onOrder={() => navigate(`${APP_ROUTES.newRequisition}?customerId=${selectedCustomer.id}`)}
                onRequisitionHistory={() => navigate(`${APP_ROUTES.requisitions}/kupac/${selectedCustomer.id}`)}
                onPromotionalMaterial={() => navigate(
                    `${APP_ROUTES.newAdvertisingMaterialDistribution}?customerId=${selectedCustomer.id}`,
                )}
            />}
        </AppLayout>
    );
}
