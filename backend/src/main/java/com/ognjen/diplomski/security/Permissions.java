package com.ognjen.diplomski.security;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class Permissions {

    public static final String CUSTOMERS_READ = "customers.read";
    public static final String CUSTOMERS_CREATE = "customers.create";
    public static final String CUSTOMERS_UPDATE = "customers.update";
    public static final String CUSTOMERS_DELETE = "customers.delete";
    public static final String CUSTOMERS_CALL = "customers.call";
    public static final String REQUISITIONS_READ = "requisitions.read";
    public static final String REQUISITIONS_CREATE = "requisitions.create";
    public static final String SALES_REPORTS_READ = "sales-reports.read";
    public static final String GOODS_REQUESTS_READ = "goods-requests.read";
    public static final String GOODS_REQUESTS_CREATE = "goods-requests.create";
    public static final String GOODS_REQUESTS_APPROVE = "goods-requests.approve";
    public static final String ADVERTISING_MATERIALS_READ = "advertising-materials.read";
    public static final String ADVERTISING_MATERIALS_CREATE = "advertising-materials.create";
    public static final String EMPLOYEES_READ = "employees.read";
    public static final String FINANCE_READ = "finance.read";
    public static final String PRODUCTION_READ = "production.read";
}
