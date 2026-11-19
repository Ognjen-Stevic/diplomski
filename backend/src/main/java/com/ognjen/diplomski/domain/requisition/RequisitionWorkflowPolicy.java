package com.ognjen.diplomski.domain.requisition;

import com.ognjen.diplomski.dto.RequisitionActionsResponse;
import com.ognjen.diplomski.dto.RequisitionRequest;
import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.security.CustomerAccessPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RequisitionWorkflowPolicy {

    private static final Set<RequisitionStatus> BOOKKEEPER_VISIBLE_STATUSES = Set.of(
            RequisitionStatus.APPROVED,
            RequisitionStatus.REJECTED_BY_BOOKKEEPER,
            RequisitionStatus.PROCESSING,
            RequisitionStatus.COMPLETED,
            RequisitionStatus.CANCELLED_BY_BOOKKEEPER
    );

    private static final Map<RequisitionStatus, List<RequisitionStatus>> BOOKKEEPER_STATUS_TRANSITIONS = Map.of(
            RequisitionStatus.APPROVED, List.of(
                    RequisitionStatus.COMPLETED,
                    RequisitionStatus.CANCELLED_BY_BOOKKEEPER,
                    RequisitionStatus.REJECTED_BY_BOOKKEEPER,
                    RequisitionStatus.PROCESSING
            ),
            RequisitionStatus.PROCESSING, List.of(
                    RequisitionStatus.COMPLETED,
                    RequisitionStatus.CANCELLED_BY_BOOKKEEPER,
                    RequisitionStatus.REJECTED_BY_BOOKKEEPER
            )
    );

    public Set<RequisitionStatus> bookkeeperVisibleStatuses() {
        return BOOKKEEPER_VISIBLE_STATUSES;
    }

    public void requireSalesRepresentative(AppUserPrincipal principal) {
        if (!principal.hasRole("SALES_REP")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only sales representatives can access requisitions"
            );
        }
    }

    public boolean canView(Requisition requisition, AppUserPrincipal principal) {
        if (principal.hasRole("CEO")) return true;
        if (principal.hasRole("SALES_REP")) {
            return requisition.getSalesRep().getId().equals(principal.id());
        }
        if (principal.hasRole("MANAGER")) {
            return requisition.getStatus() != RequisitionStatus.DRAFT
                    && isManagerTeamRequisition(requisition, principal.id());
        }
        return principal.hasRole("BOOKKEEPER")
                && BOOKKEEPER_VISIBLE_STATUSES.contains(requisition.getStatus());
    }

    public boolean canAccessCustomerRequisitions(Customer customer, AppUserPrincipal principal) {
        if (principal.hasRole("BOOKKEEPER")) return true;
        return CustomerAccessPolicy.canAccessCustomer(principal, customer)
                && CustomerAccessPolicy.visibleStatuses(principal).contains(customer.getStatus());
    }

    public RequisitionActionsResponse actionsFor(
            Requisition requisition,
            AppUserPrincipal principal
    ) {
        RequisitionStatus status = requisition.getStatus();
        if (principal.hasRole("SALES_REP")
                && requisition.getSalesRep().getId().equals(principal.id())
                && (status == RequisitionStatus.DRAFT || status == RequisitionStatus.REJECTED)) {
            return new RequisitionActionsResponse(true, List.of(RequisitionStatus.SUBMITTED));
        }
        if (principal.hasRole("MANAGER")
                && isManagerTeamRequisition(requisition, principal.id())
                && status == RequisitionStatus.SUBMITTED) {
            return new RequisitionActionsResponse(true, List.of(
                    RequisitionStatus.APPROVED,
                    RequisitionStatus.REJECTED,
                    RequisitionStatus.CANCELLED_BY_MANAGER
            ));
        }
        if (principal.hasRole("MANAGER")
                && isManagerTeamRequisition(requisition, principal.id())
                && status == RequisitionStatus.REJECTED_BY_BOOKKEEPER) {
            return new RequisitionActionsResponse(true, List.of(
                    RequisitionStatus.APPROVED,
                    RequisitionStatus.REJECTED,
                    RequisitionStatus.CANCELLED_BY_MANAGER
            ));
        }
        if (principal.hasRole("BOOKKEEPER")) {
            return new RequisitionActionsResponse(
                    false,
                    BOOKKEEPER_STATUS_TRANSITIONS.getOrDefault(status, List.of())
            );
        }
        return new RequisitionActionsResponse(false, List.of());
    }

    public void validateGratisFields(RequisitionRequest request) {
        String customReason = normalizeOptional(request.customReason());
        if (request.type() == RequisitionType.NORMAL) {
            if (request.gratisReason() != null || customReason != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Normal requisitions cannot have a gratis reason"
                );
            }
            return;
        }
        if (request.gratisReason() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gratis reason is required");
        }
        if (request.gratisReason() == GratisReason.OTHER && customReason == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Custom reason is required for OTHER"
            );
        }
        if (request.gratisReason() != GratisReason.OTHER && customReason != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Custom reason is allowed only for OTHER"
            );
        }
    }

    public boolean supportsStatusNote(RequisitionStatus status) {
        return isRejection(status)
                || status == RequisitionStatus.CANCELLED_BY_MANAGER
                || status == RequisitionStatus.CANCELLED_BY_BOOKKEEPER;
    }

    public boolean isRejection(RequisitionStatus status) {
        return status == RequisitionStatus.REJECTED
                || status == RequisitionStatus.REJECTED_BY_BOOKKEEPER;
    }

    private boolean isManagerTeamRequisition(Requisition requisition, Long managerId) {
        return requisition.getSalesRep().getManager() != null
                && requisition.getSalesRep().getManager().getId().equals(managerId);
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
