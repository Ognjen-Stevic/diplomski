package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.dto.AlertResponse;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.repository.RequisitionRepository;
import com.ognjen.diplomski.repository.GoodsRequestRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final CustomerRepository customerRepository;
    private final RequisitionRepository requisitionRepository;
    private final GoodsRequestRepository goodsRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public AlertResponse getAlerts(AppUserPrincipal principal) {
        if (principal.hasRole("MANAGER")) {
            return new AlertResponse(
                    customerRepository.countByStatusForManagerTeam(
                            CustomerStatus.DRAFT,
                            principal.id()
                    ),
                    requisitionRepository.countByStatusForManagerTeam(
                            RequisitionStatus.SUBMITTED,
                            principal.id()
                    ) + requisitionRepository.countByStatusForManagerTeam(
                            RequisitionStatus.REJECTED_BY_BOOKKEEPER,
                            principal.id()
                    ),
                    0
            );
        }

        if (principal.hasRole("SALES_REP")) {
            return new AlertResponse(
                    0,
                    requisitionRepository.countByStatusAndSalesRepId(
                            RequisitionStatus.REJECTED,
                            principal.id()
                    ),
                    0
            );
        }

        if (principal.hasRole("BOOKKEEPER")) {
            return new AlertResponse(
                    0,
                    requisitionRepository.countByStatus(RequisitionStatus.APPROVED),
                    goodsRequestRepository.countRequestsWithPendingItems()
            );
        }

        return new AlertResponse(0, 0, 0);
    }
}
