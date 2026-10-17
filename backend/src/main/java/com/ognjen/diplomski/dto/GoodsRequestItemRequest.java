package com.ognjen.diplomski.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GoodsRequestItemRequest(
        String productCode,
        Long materialId,
        @NotNull @Positive BigDecimal quantity
) {
}
