package com.ognjen.diplomski.dto;

import java.util.List;

public record ManagersSalesComparisonResponse(
        int year,
        int month,
        List<ManagerSalesComparisonResponse> managers
) {
}
