package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.CustomerStatus;
import jakarta.validation.constraints.NotNull;

public record CustomerStatusUpdateRequest(
        @NotNull CustomerStatus status
) {}
