package com.ognjen.diplomski.dto;

import java.math.BigDecimal;

public record GratisReportSummaryResponse(
        BigDecimal totalItems,
        BigDecimal totalBottles,
        BigDecimal totalJugs,
        BigDecimal totalLiters
) {
}
