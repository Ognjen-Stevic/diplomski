package com.ognjen.diplomski.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CustomerSalesRepresentativeUpdateRequest(
        @NotNull @Positive Long salesRepId
) {}
