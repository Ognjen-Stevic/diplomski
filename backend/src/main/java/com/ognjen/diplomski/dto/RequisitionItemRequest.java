package com.ognjen.diplomski.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RequisitionItemRequest(
        @NotBlank String productCode,
        @NotNull @DecimalMin(value = "0.01") BigDecimal quantity
) {
}
