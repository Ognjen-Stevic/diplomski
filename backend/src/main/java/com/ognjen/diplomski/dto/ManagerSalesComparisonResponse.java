package com.ognjen.diplomski.dto;

import java.math.BigDecimal;

public record ManagerSalesComparisonResponse(
        Long managerId,
        String firstName,
        String lastName,
        BigDecimal bottlesSold,
        BigDecimal balloonsSold,
        BigDecimal gratisBottles,
        BigDecimal gratisBalloons,
        BigDecimal stimulation
) {
}
