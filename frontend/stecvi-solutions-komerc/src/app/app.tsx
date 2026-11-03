import AiPage from "../features/ai/ai_page";
import { ErrorBoundary } from "./error_boundary";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import CustomersPage from "../features/customer/pages/customers_page";
import NewCustomerPage from "../features/customer/pages/new_customer_page";
import HomePage from "../features/dashboard/pages/home_page";
import ReportPage from "../features/report/pages/report_page";
import RequisitionPage from "../features/requisition/pages/requisition_page";
import NewRequisitionPage from "../features/requisition/pages/new_requisition_page";
import RequisitionHistoryPage from "../features/requisition/pages/requisition_history_page";
import EditRequisitionPage from "../features/requisition/pages/edit_requisition_page";
import NewGoodsRequestPage from "../features/goods_request/pages/new_goods_request_page";
import NewAdvertisingMaterialDistributionPage from "../features/advertising_materials/pages/new_advertising_material_distribution_page";
import GoodsRequestPage from "../features/goods_request/pages/goods_request_page";
import AdvertisingMaterialHistoryPage from "../features/advertising_materials/pages/advertising_material_history_page";
import AuthForm from "../features/auth/pages/auth_form";
import { GuestRoute, ProtectedRoute } from "../features/auth/components/auth_route";
import TeamPage from "../features/sales_team/pages/team_page";
import EmployeesPage from "../features/employees/pages/employees_page";
import ProductsManagementPage from "../features/product/pages/products_management_page";
import AdvertisingMaterialManagementPage from "../features/advertising_materials/pages/advertising_material_management_page";
import { APP_ROUTES } from "./routes";

export default function App() {
  return (
    <ErrorBoundary><BrowserRouter>
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
    </BrowserRouter></ErrorBoundary>
  )
}
