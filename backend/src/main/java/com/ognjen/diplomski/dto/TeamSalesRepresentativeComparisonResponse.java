package com.ognjen.diplomski.dto;

import java.math.BigDecimal;

public record TeamSalesRepresentativeComparisonResponse(
        Long salesRepId,
        String firstName,
        String lastName,
        BigDecimal bottlesSold,
        BigDecimal balloonsSold,
        BigDecimal gratisBottles,
        BigDecimal gratisBalloons,
        BigDecimal stimulation
) {
}
