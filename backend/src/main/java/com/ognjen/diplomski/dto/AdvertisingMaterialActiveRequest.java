package com.ognjen.diplomski.dto;

import jakarta.validation.constraints.NotNull;
public record AdvertisingMaterialActiveRequest(@NotNull(message = "Active value is required") Boolean active) {
}
