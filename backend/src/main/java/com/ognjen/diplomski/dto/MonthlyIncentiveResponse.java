package com.ognjen.diplomski.dto;

import java.math.BigDecimal;

public record MonthlyIncentiveResponse(
        int month,
        BigDecimal totalIncentive
) {
}
