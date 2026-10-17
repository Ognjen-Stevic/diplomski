package com.ognjen.diplomski.dto;

import java.util.List;

public record YtdSalesReportResponse(
        int year,
        SalesRepresentativeOptionResponse salesRep,
        SalesReportSummaryResponse summary,
        List<ProductSalesReportResponse> products,
        List<MonthlyIncentiveResponse> monthlyIncentives
) {
}
