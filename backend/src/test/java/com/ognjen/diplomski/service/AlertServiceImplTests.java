package com.ognjen.diplomski.service;

import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.repository.RequisitionRepository;
import com.ognjen.diplomski.repository.GoodsRequestRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.impl.AlertServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlertServiceImplTests {

    @Test
    void managerReceivesOnlyTeamAlertCounts() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        RequisitionRepository requisitionRepository = mock(RequisitionRepository.class);
        GoodsRequestRepository goodsRequestRepository = mock(GoodsRequestRepository.class);
        AlertServiceImpl service = new AlertServiceImpl(customerRepository, requisitionRepository, goodsRequestRepository);
        when(customerRepository.countByStatusForManagerTeam(CustomerStatus.DRAFT, 7L)).thenReturn(3L);
        when(requisitionRepository.countByStatusForManagerTeam(RequisitionStatus.SUBMITTED, 7L)).thenReturn(2L);
        when(requisitionRepository.countByStatusForManagerTeam(
                RequisitionStatus.REJECTED_BY_BOOKKEEPER,
                7L
        )).thenReturn(1L);

        var alerts = service.getAlerts(principal(7L, "ROLE_MANAGER"));

        assertEquals(3L, alerts.draftCustomers());
        assertEquals(3L, alerts.requisitionAlerts());
        assertEquals(0L, alerts.pendingGoodsRequests());
        verify(customerRepository).countByStatusForManagerTeam(CustomerStatus.DRAFT, 7L);
        verify(requisitionRepository).countByStatusForManagerTeam(RequisitionStatus.SUBMITTED, 7L);
        verify(requisitionRepository).countByStatusForManagerTeam(
                RequisitionStatus.REJECTED_BY_BOOKKEEPER,
                7L
        );
    }

    @Test
    void bookkeeperReceivesApprovedRequisitionCount() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        RequisitionRepository requisitionRepository = mock(RequisitionRepository.class);
        GoodsRequestRepository goodsRequestRepository = mock(GoodsRequestRepository.class);
        AlertServiceImpl service = new AlertServiceImpl(customerRepository, requisitionRepository, goodsRequestRepository);
        when(requisitionRepository.countByStatus(RequisitionStatus.APPROVED)).thenReturn(5L);
        when(goodsRequestRepository.countRequestsWithPendingItems()).thenReturn(3L);

        var alerts = service.getAlerts(principal(9L, "ROLE_BOOKKEEPER"));

        assertEquals(0L, alerts.draftCustomers());
        assertEquals(5L, alerts.requisitionAlerts());
        assertEquals(3L, alerts.pendingGoodsRequests());
        verifyNoInteractions(customerRepository);
    }

    @Test
    void salesRepresentativeReceivesOwnRejectedRequisitionCount() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        RequisitionRepository requisitionRepository = mock(RequisitionRepository.class);
        GoodsRequestRepository goodsRequestRepository = mock(GoodsRequestRepository.class);
        AlertServiceImpl service = new AlertServiceImpl(customerRepository, requisitionRepository, goodsRequestRepository);
        when(requisitionRepository.countByStatusAndSalesRepId(RequisitionStatus.REJECTED, 20L)).thenReturn(4L);

        var alerts = service.getAlerts(principal(20L, "ROLE_SALES_REP"));

        assertEquals(0L, alerts.draftCustomers());
        assertEquals(4L, alerts.requisitionAlerts());
        assertEquals(0L, alerts.pendingGoodsRequests());
        verifyNoInteractions(customerRepository);
    }

    private static AppUserPrincipal principal(Long id, String role) {
        return new AppUserPrincipal(
                id,
                "user@example.com",
                "password-hash",
                "Test",
                "User",
                true,
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
