package com.ognjen.diplomski.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AdvertisingMaterialDistributionItemRequest(
        @NotNull @Positive Long materialId,
        @NotNull @Positive BigDecimal quantity
) {
}
