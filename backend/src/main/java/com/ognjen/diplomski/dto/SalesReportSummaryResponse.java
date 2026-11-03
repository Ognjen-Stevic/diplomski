package com.ognjen.diplomski.dto;

import java.math.BigDecimal;

public record SalesReportSummaryResponse(
        BigDecimal totalItems,
        BigDecimal totalBottles,
        BigDecimal totalJugs,
        BigDecimal totalLiters,
        BigDecimal totalIncentive,
        BigDecimal salesRepIncentive,
        BigDecimal managerIncentive
) {
    public SalesReportSummaryResponse(BigDecimal totalItems, BigDecimal totalBottles,
            BigDecimal totalJugs, BigDecimal totalLiters, BigDecimal totalIncentive) {
        this(totalItems, totalBottles, totalJugs, totalLiters, totalIncentive, null, null);
    }
}
