package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.RequisitionStatus;

import java.util.List;

public record RequisitionActionsResponse(
        boolean canEdit,
        List<RequisitionStatus> allowedStatusTransitions
) {
}
