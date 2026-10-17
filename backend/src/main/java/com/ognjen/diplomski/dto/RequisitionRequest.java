package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RequisitionRequest(
        @NotNull Long customerId,
        @NotNull RequisitionType type,
        GratisReason gratisReason,
        String customReason,
        String note,
        @NotEmpty List<@Valid RequisitionItemRequest> items
) {
}
