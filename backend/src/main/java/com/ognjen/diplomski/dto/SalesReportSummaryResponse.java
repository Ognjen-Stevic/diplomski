package com.ognjen.diplomski.dto;

import java.math.BigDecimal;

public record SalesReportSummaryResponse(
        BigDecimal totalItems,
        BigDecimal totalBottles,
        BigDecimal totalJugs,
        BigDecimal totalLiters,
        BigDecimal totalIncentive
) {
}
