package com.ognjen.diplomski.dto;

import java.util.List;

public record MonthlySalesReportResponse(
        int year,
        int month,
        SalesRepresentativeOptionResponse salesRep,
        SalesReportSummaryResponse summary,
        List<ProductSalesReportResponse> products
) {
}
