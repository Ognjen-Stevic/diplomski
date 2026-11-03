package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record RequisitionRequest(
        @NotNull @Positive Long customerId,
        @NotNull RequisitionType type,
        GratisReason gratisReason,
        String customReason,
        String note,
        @NotEmpty List<@NotNull @Valid RequisitionItemRequest> items,
        String expectedRevision
) {
    public RequisitionRequest(Long customerId, RequisitionType type, GratisReason gratisReason,
            String customReason, String note, List<RequisitionItemRequest> items) {
        this(customerId, type, gratisReason, customReason, note, items, null);
    }
}
