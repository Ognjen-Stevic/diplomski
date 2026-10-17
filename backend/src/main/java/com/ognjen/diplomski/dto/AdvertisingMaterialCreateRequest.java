package com.ognjen.diplomski.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdvertisingMaterialCreateRequest(
        @NotBlank @Size(max = 150) String name
) {
}
