
export const PERMISSIONS = {
  CUSTOMERS_READ: "customers.read",
  CUSTOMERS_CREATE: "customers.create",
  CUSTOMERS_UPDATE: "customers.update",
  CUSTOMERS_DELETE: "customers.delete",
  CUSTOMERS_CALL: "customers.call",
  REQUISITIONS_READ: "requisitions.read",
  REQUISITIONS_CREATE: "requisitions.create",
  SALES_REPORTS_READ: "sales-reports.read",
  GOODS_REQUESTS_READ: "goods-requests.read",
  GOODS_REQUESTS_CREATE: "goods-requests.create",
  GOODS_REQUESTS_APPROVE: "goods-requests.approve",
  ADVERTISING_MATERIALS_READ: "advertising-materials.read",
  ADVERTISING_MATERIALS_CREATE: "advertising-materials.create",
  EMPLOYEES_READ: "employees.read",
  FINANCE_READ: "finance.read",
  PRODUCTION_READ: "production.read",
} as const;

export type Permission = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];

