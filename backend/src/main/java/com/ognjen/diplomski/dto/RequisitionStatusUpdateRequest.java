package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import jakarta.validation.constraints.NotNull;

public record RequisitionStatusUpdateRequest(
        @NotNull RequisitionStatus status,
        String note
) {
}
