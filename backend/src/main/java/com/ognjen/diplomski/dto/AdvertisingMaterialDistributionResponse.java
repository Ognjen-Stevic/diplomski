package com.ognjen.diplomski.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdvertisingMaterialDistributionResponse(
        Long id,
        SalesRepresentativeOptionResponse salesRep,
        CustomerSummaryResponse customer,
        String materialName,
        BigDecimal quantity,
        String note,
        OffsetDateTime distributedAt
) {
}
