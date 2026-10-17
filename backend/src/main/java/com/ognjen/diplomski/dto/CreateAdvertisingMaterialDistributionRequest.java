package com.ognjen.diplomski.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateAdvertisingMaterialDistributionRequest(
        @NotNull @Positive Long customerId,
        @NotEmpty List<@Valid AdvertisingMaterialDistributionItemRequest> items,
        @Size(max = 5000) String note
) {
}
