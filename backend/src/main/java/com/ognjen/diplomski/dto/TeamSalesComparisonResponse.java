package com.ognjen.diplomski.dto;

import java.math.BigDecimal;
import java.util.List;

public record TeamSalesComparisonResponse(
        int year,
        int month,
        List<TeamSalesRepresentativeComparisonResponse> salesRepresentatives,
        BigDecimal managerStimulation
) {
}
