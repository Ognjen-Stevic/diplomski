package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.DashboardMonthlyStatisticsResponse;
import com.ognjen.diplomski.dto.RequisitionEmailRecipient;
import com.ognjen.diplomski.dto.RequisitionRequest;
import com.ognjen.diplomski.dto.RequisitionResponse;
import com.ognjen.diplomski.dto.RequisitionPageResponse;
import com.ognjen.diplomski.dto.RequisitionSummaryPageResponse;
import com.ognjen.diplomski.dto.RequisitionStatusUpdateRequest;
import com.ognjen.diplomski.security.AppUserPrincipal;

public interface RequisitionService {

    RequisitionResponse create(AppUserPrincipal principal, RequisitionRequest request);

    RequisitionResponse findById(AppUserPrincipal principal, Long requisitionId);

    byte[] generatePdf(AppUserPrincipal principal, Long requisitionId);

    void sendPdfByEmail(
            AppUserPrincipal principal,
            Long requisitionId,
            RequisitionEmailRecipient recipient
    );

    RequisitionResponse update(AppUserPrincipal principal, Long requisitionId, RequisitionRequest request);

    RequisitionResponse updateStatus(
            AppUserPrincipal principal,
            Long requisitionId,
            RequisitionStatusUpdateRequest request
    );

    DashboardMonthlyStatisticsResponse getDashboardMonthlyStatistics(
            AppUserPrincipal principal,
            int year,
            int month
    );

    RequisitionPageResponse getMonthlyRequisitionPage(
            AppUserPrincipal principal,
            int year,
            int month,
            int page,
            String search,
            com.ognjen.diplomski.entity.enums.RequisitionStatus status
    );

    RequisitionSummaryPageResponse getMonthlyCustomerRequisitionPage(
            AppUserPrincipal principal,
            Long customerId,
            int year,
            int month,
            int page
    );

}
