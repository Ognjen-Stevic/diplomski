package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.MonthlyRequisitionResponse;
import com.ognjen.diplomski.dto.RequisitionEmailRecipient;
import com.ognjen.diplomski.dto.RequisitionRequest;
import com.ognjen.diplomski.dto.RequisitionResponse;
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

    MonthlyRequisitionResponse getMonthlyRequisition(AppUserPrincipal principal, int year, int month);

    MonthlyRequisitionResponse getMonthlyCustomerRequisitions(
            AppUserPrincipal principal,
            Long customerId,
            int year,
            int month
    );
}
