package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import java.time.OffsetDateTime;

public record RequisitionListSummaryResponse(
        Long id,
        Long salesRepId,
        String salesRepFirstName,
        String salesRepLastName,
        String salesRepEmail,
        Long buyerId,
        String buyerObjectName,
        String buyerCompanyName,
        String buyerEmail,
        RequisitionType type,
        OffsetDateTime createdAt,
        RequisitionStatus status
) {
}
