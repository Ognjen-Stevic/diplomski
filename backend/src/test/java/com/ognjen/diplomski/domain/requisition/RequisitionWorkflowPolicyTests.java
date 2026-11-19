package com.ognjen.diplomski.domain.requisition;

import com.ognjen.diplomski.dto.RequisitionItemRequest;
import com.ognjen.diplomski.dto.RequisitionRequest;
import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequisitionWorkflowPolicyTests {

    private final RequisitionWorkflowPolicy policy = new RequisitionWorkflowPolicy();

    @Test
    void salesRepresentativeCanEditOwnDraftAndSubmitIt() {
        Requisition requisition = requisition(20L, null, RequisitionStatus.DRAFT);

        var actions = policy.actionsFor(requisition, principal(20L, "SALES_REP"));

        assertTrue(actions.canEdit());
        assertEquals(List.of(RequisitionStatus.SUBMITTED), actions.allowedStatusTransitions());
        assertTrue(policy.canView(requisition, principal(20L, "SALES_REP")));
        assertFalse(policy.canView(requisition, principal(21L, "SALES_REP")));
    }

    @Test
    void managerCanProcessOnlySubmittedRequisitionFromOwnTeam() {
        Requisition submitted = requisition(20L, 7L, RequisitionStatus.SUBMITTED);
        Requisition draft = requisition(20L, 7L, RequisitionStatus.DRAFT);

        var actions = policy.actionsFor(submitted, principal(7L, "MANAGER"));

        assertTrue(actions.canEdit());
        assertEquals(
                List.of(
                        RequisitionStatus.APPROVED,
                        RequisitionStatus.REJECTED,
                        RequisitionStatus.CANCELLED_BY_MANAGER
                ),
                actions.allowedStatusTransitions()
        );
        assertTrue(policy.canView(submitted, principal(7L, "MANAGER")));
        assertFalse(policy.canView(draft, principal(7L, "MANAGER")));
        assertFalse(policy.canView(submitted, principal(8L, "MANAGER")));
    }

    @Test
    void bookkeeperTransitionsAndVisibilityRemainStatusBased() {
        Requisition approved = requisition(20L, 7L, RequisitionStatus.APPROVED);
        Requisition rejected = requisition(
                20L, 7L, RequisitionStatus.REJECTED_BY_BOOKKEEPER);

        var actions = policy.actionsFor(approved, principal(30L, "BOOKKEEPER"));

        assertFalse(actions.canEdit());
        assertEquals(
                List.of(
                        RequisitionStatus.COMPLETED,
                        RequisitionStatus.CANCELLED_BY_BOOKKEEPER,
                        RequisitionStatus.REJECTED_BY_BOOKKEEPER,
                        RequisitionStatus.PROCESSING
                ),
                actions.allowedStatusTransitions()
        );
        assertTrue(policy.canView(approved, principal(30L, "BOOKKEEPER")));
        assertTrue(policy.canView(rejected, principal(30L, "BOOKKEEPER")));
        assertFalse(policy.canView(
                requisition(20L, 7L, RequisitionStatus.SUBMITTED),
                principal(30L, "BOOKKEEPER")
        ));
    }

    @Test
    void responsibleManagerCanRejectBookkeeperRejectionButOtherManagersCannot() {
        Requisition rejected = requisition(
                20L, 7L, RequisitionStatus.REJECTED_BY_BOOKKEEPER);

        assertEquals(
                List.of(
                        RequisitionStatus.APPROVED,
                        RequisitionStatus.REJECTED,
                        RequisitionStatus.CANCELLED_BY_MANAGER
                ),
                policy.actionsFor(rejected, principal(7L, "MANAGER"))
                        .allowedStatusTransitions()
        );
        assertTrue(policy.actionsFor(rejected, principal(8L, "MANAGER"))
                .allowedStatusTransitions().isEmpty());
        assertTrue(policy.actionsFor(rejected, principal(20L, "SALES_REP"))
                .allowedStatusTransitions().isEmpty());
    }

    @Test
    void customerRequisitionAccessReusesSharedCustomerPolicy() {
        Customer customer = new Customer();
        customer.setStatus(CustomerStatus.DEACTIVATED);
        AppUser manager = user(7L, null);
        customer.setSalesRep(user(20L, manager));

        assertTrue(policy.canAccessCustomerRequisitions(customer, principal(1L, "CEO")));
        assertTrue(policy.canAccessCustomerRequisitions(customer, principal(7L, "MANAGER")));
        assertTrue(policy.canAccessCustomerRequisitions(customer, principal(30L, "BOOKKEEPER")));
        assertFalse(policy.canAccessCustomerRequisitions(customer, principal(20L, "SALES_REP")));
    }

    @Test
    void gratisFieldValidationPreservesExistingMessages() {
        RequisitionRequest missingReason = request(RequisitionType.GRATIS, null, null);
        RequisitionRequest normalWithReason = request(RequisitionType.NORMAL, GratisReason.GIFT, null);

        ResponseStatusException missingReasonError = assertThrows(
                ResponseStatusException.class,
                () -> policy.validateGratisFields(missingReason)
        );
        ResponseStatusException normalError = assertThrows(
                ResponseStatusException.class,
                () -> policy.validateGratisFields(normalWithReason)
        );

        assertEquals("Gratis reason is required", missingReasonError.getReason());
        assertEquals("Normal requisitions cannot have a gratis reason", normalError.getReason());
    }

    private RequisitionRequest request(RequisitionType type, GratisReason reason, String customReason) {
        return new RequisitionRequest(
                100L,
                type,
                reason,
                customReason,
                null,
                List.of(new RequisitionItemRequest("P-1", BigDecimal.ONE))
        );
    }

    private Requisition requisition(Long salesRepId, Long managerId, RequisitionStatus status) {
        Requisition requisition = new Requisition();
        requisition.setSalesRep(user(salesRepId, managerId == null ? null : user(managerId, null)));
        requisition.setStatus(status);
        return requisition;
    }

    private AppUser user(Long id, AppUser manager) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setManager(manager);
        return user;
    }

    private AppUserPrincipal principal(Long id, String role) {
        return new AppUserPrincipal(
                id,
                role.toLowerCase() + "@example.com",
                "password-hash",
                "Test",
                role,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
