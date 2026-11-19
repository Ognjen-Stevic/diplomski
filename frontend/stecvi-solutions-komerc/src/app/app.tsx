import { lazy, Suspense } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";

import { GuestRoute, ProtectedRoute } from "../features/auth/components/auth_route";
import { ErrorBoundary } from "./error_boundary";
import { APP_ROUTES } from "./routes";

const AiPage = lazy(() => import("../features/ai/pages/ai_page"));
const CustomersPage = lazy(() => import("../features/customer/pages/customers_page"));
const NewCustomerPage = lazy(() => import("../features/customer/pages/new_customer_page"));
const HomePage = lazy(() => import("../features/dashboard/pages/home_page"));
const ReportPage = lazy(() => import("../features/report/pages/report_page"));
const RequisitionPage = lazy(() => import("../features/requisition/pages/requisition_page"));
const NewRequisitionPage = lazy(() => import("../features/requisition/pages/new_requisition_page"));
const RequisitionHistoryPage = lazy(() => import("../features/requisition/pages/requisition_history_page"));
const EditRequisitionPage = lazy(() => import("../features/requisition/pages/edit_requisition_page"));
const NewGoodsRequestPage = lazy(() => import("../features/goods_request/pages/new_goods_request_page"));
const NewAdvertisingMaterialDistributionPage = lazy(() => import("../features/advertising_materials/pages/new_advertising_material_distribution_page"));
const GoodsRequestPage = lazy(() => import("../features/goods_request/pages/goods_request_page"));
const AdvertisingMaterialHistoryPage = lazy(() => import("../features/advertising_materials/pages/advertising_material_history_page"));
const AuthForm = lazy(() => import("../features/auth/pages/auth_form"));
const TeamPage = lazy(() => import("../features/sales_team/pages/team_page"));
const EmployeesPage = lazy(() => import("../features/employees/pages/employees_page"));
const ProductsManagementPage = lazy(() => import("../features/product/pages/products_management_page"));
const AdvertisingMaterialManagementPage = lazy(() => import("../features/advertising_materials/pages/advertising_material_management_page"));

function RouteLoadingFallback() {
    return (
        <main
            aria-busy="true"
            aria-label="Učitavanje stranice"
            className="flex min-h-screen items-center justify-center bg-dust-grey/20 p-6"
        >
            <div className="flex items-center gap-3 rounded-2xl bg-white px-6 py-4 font-semibold text-yale-blue shadow-sm">
                <span
                    aria-hidden="true"
                    className="h-5 w-5 animate-spin rounded-full border-2 border-stormy-teal/30 border-t-stormy-teal"
                />
                Učitavanje...
            </div>
        </main>
    );
}

export default function App() {
    return (
        <ErrorBoundary>
            <BrowserRouter>
                <Suspense fallback={<RouteLoadingFallback />}>
                    <Routes>
                        <Route element={<GuestRoute />}>
                            <Route path={APP_ROUTES.login} element={<AuthForm />} />
                        </Route>
                        <Route element={<ProtectedRoute />}>
                            <Route path={APP_ROUTES.ai} element={<AiPage />} />
                            <Route path={APP_ROUTES.home} element={<HomePage />} />
                            <Route path={APP_ROUTES.customers} element={<CustomersPage />} />
                            <Route path={APP_ROUTES.newCustomer} element={<NewCustomerPage />} />
                            <Route path="/kupci/:customerId/izmena" element={<NewCustomerPage />} />
                            <Route path={APP_ROUTES.reports} element={<ReportPage />} />
                            <Route path={APP_ROUTES.team} element={<TeamPage />} />
                            <Route path={APP_ROUTES.employees} element={<EmployeesPage />} />
                            <Route path={APP_ROUTES.products} element={<ProductsManagementPage />} />
                            <Route path={APP_ROUTES.requisitions} element={<RequisitionPage />} />
                            <Route path={APP_ROUTES.newRequisition} element={<NewRequisitionPage />} />
                            <Route path="/trebovanja/:requisitionId/izmena" element={<EditRequisitionPage />} />
                            <Route path="/trebovanja/kupac/:customerId" element={<RequisitionHistoryPage />} />
                            <Route path={APP_ROUTES.goodsRequests} element={<GoodsRequestPage />} />
                            <Route path={APP_ROUTES.newGoodsRequest} element={<NewGoodsRequestPage />} />
                            <Route path={APP_ROUTES.advertisingMaterials} element={<AdvertisingMaterialHistoryPage />} />
                            <Route path={APP_ROUTES.advertisingMaterialManagement} element={<AdvertisingMaterialManagementPage />} />
                            <Route path={APP_ROUTES.newAdvertisingMaterialDistribution} element={<NewAdvertisingMaterialDistributionPage />} />
                        </Route>
                        <Route path="*" element={<main className="p-6"><h1 className="text-2xl font-bold">Stranica nije pronađena</h1><a href={APP_ROUTES.home}>Početna</a></main>} />
                    </Routes>
                </Suspense>
            </BrowserRouter>
        </ErrorBoundary>
    );
}
