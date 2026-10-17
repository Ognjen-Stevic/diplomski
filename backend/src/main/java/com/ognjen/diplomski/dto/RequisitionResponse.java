package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record RequisitionResponse(
        Long id,
        SalesRepresentativeSummary salesRep,
        RequisitionBuyerResponse buyer,
        RequisitionType type,
        List<RequisitionItemResponse> items,
        GratisReason gratisReason,
        String customReason,
        String note,
        OffsetDateTime createdAt,
        RequisitionStatus status,
        String statusNote,
        RequisitionActionsResponse actions
) {
    public static RequisitionResponse from(
            Requisition requisition,
            RequisitionActionsResponse actions
    ) {
        return new RequisitionResponse(
                requisition.getId(),
                new SalesRepresentativeSummary(
                        requisition.getSalesRep().getId(),
                        requisition.getSalesRep().getFirstName(),
                        requisition.getSalesRep().getLastName(),
                        requisition.getSalesRep().getEmail()
                ),
                RequisitionBuyerResponse.from(requisition.getCustomer()),
                requisition.getType(),
                requisition.getItems().stream().map(RequisitionItemResponse::from).toList(),
                requisition.getGratisReason(),
                requisition.getCustomReason(),
                requisition.getNote(),
                requisition.getCreatedAt(),
                requisition.getStatus(),
                requisition.getStatusNote(),
                actions
        );
    }

    public record SalesRepresentativeSummary(
            Long id,
            String firstName,
            String lastName,
            String email
    ) {}
}
