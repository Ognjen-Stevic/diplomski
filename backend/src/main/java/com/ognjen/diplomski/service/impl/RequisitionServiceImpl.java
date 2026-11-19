package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.dto.DashboardMonthlyStatisticsResponse;
import com.ognjen.diplomski.dto.RequisitionActionsResponse;
import com.ognjen.diplomski.dto.RequisitionEmailRecipient;
import com.ognjen.diplomski.dto.RequisitionRequest;
import com.ognjen.diplomski.dto.RequisitionResponse;
import com.ognjen.diplomski.dto.RequisitionPageResponse;
import com.ognjen.diplomski.dto.RequisitionSummaryPageResponse;
import com.ognjen.diplomski.dto.RequisitionStatusUpdateRequest;
import com.ognjen.diplomski.domain.requisition.RequisitionPeriodFactory;
import com.ognjen.diplomski.domain.requisition.RequisitionPeriodFactory.RequisitionPeriod;
import com.ognjen.diplomski.domain.requisition.RequisitionWorkflowPolicy;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.repository.RequisitionRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.RequisitionItemSynchronizer;
import com.ognjen.diplomski.service.RequisitionService;
import com.ognjen.diplomski.service.RequisitionPdfService;
import com.ognjen.diplomski.service.RequisitionEmailService;
import com.ognjen.diplomski.ai.lifecycle.capture.AiCaptureOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RequisitionServiceImpl implements RequisitionService {

    private static final int REQUISITION_PAGE_SIZE = 20;

    private final RequisitionRepository requisitionRepository;
    private final CustomerRepository customerRepository;
    private final AppUserRepository appUserRepository;
    private final RequisitionPdfService requisitionPdfService;
    private final RequisitionEmailService requisitionEmailService;
    private final RequisitionItemSynchronizer itemSynchronizer;
    private final RequisitionWorkflowPolicy workflowPolicy = new RequisitionWorkflowPolicy();
    private final RequisitionPeriodFactory periodFactory = new RequisitionPeriodFactory();
    private AiCaptureOutbox aiCaptureOutbox;

    @Autowired(required = false)
    void setAiCaptureOutbox(AiCaptureOutbox aiCaptureOutbox) {
        this.aiCaptureOutbox = aiCaptureOutbox;
    }

    @Override
    @Transactional
    public RequisitionResponse create(AppUserPrincipal principal, RequisitionRequest request) {
        workflowPolicy.requireSalesRepresentative(principal);
        workflowPolicy.validateGratisFields(request);

        var customer = customerRepository.findByIdAndSalesRepIdAndStatus(
                        request.customerId(),
                        principal.id(),
                        CustomerStatus.APPROVED
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        Requisition requisition = new Requisition();
        requisition.setCustomer(customer);
        requisition.setSalesRep(appUserRepository.getReferenceById(principal.id()));
        requisition.setType(request.type());
        requisition.setStatus(RequisitionStatus.DRAFT);
        synchronizeEditableFields(requisition, request);

        return toResponse(requisitionRepository.saveAndFlush(requisition), principal);
    }

    @Override
    @Transactional(readOnly = true)
    public RequisitionResponse findById(AppUserPrincipal principal, Long requisitionId) {
        Requisition requisition = requisitionRepository.findDetailedById(requisitionId)
                .filter(item -> workflowPolicy.canView(item, principal))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requisition not found"));
        return toResponse(requisition, principal);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(AppUserPrincipal principal, Long requisitionId) {
        Requisition requisition = requisitionRepository.findDetailedById(requisitionId)
                .filter(item -> workflowPolicy.canView(item, principal))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requisition not found"));
        if (requisition.getStatus() != RequisitionStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "PDF is available only for completed requisitions"
            );
        }
        return requisitionPdfService.createPdf(requisition);
    }

    @Override
    @Transactional(readOnly = true)
    public void sendPdfByEmail(
            AppUserPrincipal principal,
            Long requisitionId,
            RequisitionEmailRecipient recipient
    ) {
        if (!principal.hasRole("BOOKKEEPER")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only bookkeepers can email requisitions"
            );
        }

        Requisition requisition = requisitionRepository.findDetailedById(requisitionId)
                .filter(item -> workflowPolicy.canView(item, principal))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Requisition not found"
                ));
        if (requisition.getStatus() != RequisitionStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is available only for completed requisitions"
            );
        }

        String recipientEmail = switch (recipient) {
            case SALES_REP -> requisition.getSalesRep().getEmail();
            case CUSTOMER -> requisition.getCustomer().getEmail();
        };
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Selected recipient does not have an email address"
            );
        }

        byte[] pdf = requisitionPdfService.createPdf(requisition);
        requisitionEmailService.sendPdf(requisition, recipientEmail, pdf);
    }

    @Override
    @Transactional
    public RequisitionResponse update(
            AppUserPrincipal principal,
            Long requisitionId,
            RequisitionRequest request
    ) {
        Requisition requisition = lockForMutation(requisitionId);
        if (workflowPolicy.canView(requisition, principal)) {
            requireCurrentRevision(requisition, request.expectedRevision());
        }
        if (!workflowPolicy.actionsFor(requisition, principal).canEdit()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requisition cannot be edited");
        }
        if (!requisition.getCustomer().getId().equals(request.customerId())
                || requisition.getType() != request.type()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer and requisition type cannot be changed"
            );
        }

        workflowPolicy.validateGratisFields(request);
        synchronizeEditableFields(requisition, request);
        requisitionRepository.flush();
        return toResponse(requisition, principal);
    }

    @Override
    @Transactional
    public RequisitionResponse updateStatus(
            AppUserPrincipal principal,
            Long requisitionId,
            RequisitionStatusUpdateRequest request
    ) {
        Requisition requisition = lockForMutation(requisitionId);
        RequisitionActionsResponse actions = workflowPolicy.actionsFor(requisition, principal);
        if (!actions.allowedStatusTransitions().contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Status transition is not allowed");
        }

        requireCurrentRevision(requisition, request.expectedRevision());
        RequisitionStatus previousStatus = requisition.getStatus();
        String statusNote = normalizeOptional(request.note());
        if (workflowPolicy.isRejection(request.status()) && statusNote == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection note is required");
        }

        requisition.setStatus(request.status());
        requisition.setStatusNote(workflowPolicy.supportsStatusNote(request.status()) ? statusNote : null);
        if (request.status() == RequisitionStatus.SUBMITTED) {
            requisition.setSentAt(OffsetDateTime.now());
        }
        Requisition saved = requisitionRepository.saveAndFlush(requisition);
        if (previousStatus != RequisitionStatus.COMPLETED
                && saved.getStatus() == RequisitionStatus.COMPLETED
                && saved.getType() == RequisitionType.NORMAL
                && aiCaptureOutbox != null) {
            aiCaptureOutbox.afterCommit(saved.getId());
        }
        return toResponse(saved, principal);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardMonthlyStatisticsResponse getDashboardMonthlyStatistics(
            AppUserPrincipal principal,
            int year,
            int month
    ) {
        RequisitionPeriod period = periodFactory.monthly(year, month);
        if (principal.hasRole("CEO")) {
            return requisitionRepository.getMonthlyStatistics(period.start(), period.end());
        }
        if (principal.hasRole("MANAGER")) {
            return requisitionRepository.getMonthlyStatisticsForManagerTeam(
                    principal.id(), period.start(), period.end());
        }
        if (principal.hasRole("SALES_REP")) {
            return requisitionRepository.getMonthlyStatisticsBySalesRep(
                    principal.id(), period.start(), period.end());
        }
        if (principal.hasRole("BOOKKEEPER")) {
            return requisitionRepository.getMonthlyStatisticsByStatuses(
                    workflowPolicy.bookkeeperVisibleStatuses(), period.start(), period.end());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User cannot access requisitions");
    }

    @Override
    @Transactional(readOnly = true)
    public RequisitionPageResponse getMonthlyRequisitionPage(
            AppUserPrincipal principal,
            int year,
            int month,
            int page,
            String search,
            RequisitionStatus status
    ) {
        RequisitionPeriod period = periodFactory.monthly(year, month);
        String normalizedSearch = normalizeSearch(search);
        boolean searchEnabled = normalizedSearch != null;
        String searchPattern = searchEnabled ? "%" + normalizedSearch + "%" : "";
        PageRequest pageable = PageRequest.of(
                page,
                REQUISITION_PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<com.ognjen.diplomski.dto.RequisitionListSummaryResponse> result;
        long gratisCount;
        long normalCount;

        if (principal.hasRole("CEO")) {
            result = requisitionRepository.findMonthlyPage(
                    period.start(), period.end(), null, status,
                    searchEnabled, searchPattern, pageable);
            gratisCount = requisitionRepository.countMonthlyByType(
                    RequisitionType.GRATIS, period.start(), period.end());
            normalCount = requisitionRepository.countMonthlyByType(
                    RequisitionType.NORMAL, period.start(), period.end());
        } else if (principal.hasRole("MANAGER")) {
            result = requisitionRepository.findMonthlyPageForManagerTeam(
                    principal.id(), period.start(), period.end(), null, status,
                    searchEnabled, searchPattern, pageable);
            gratisCount = requisitionRepository.countMonthlyForManagerTeamByType(
                    principal.id(), RequisitionType.GRATIS, period.start(), period.end());
            normalCount = requisitionRepository.countMonthlyForManagerTeamByType(
                    principal.id(), RequisitionType.NORMAL, period.start(), period.end());
        } else if (principal.hasRole("SALES_REP")) {
            result = requisitionRepository.findMonthlyPageBySalesRep(
                    principal.id(), period.start(), period.end(), null, status,
                    searchEnabled, searchPattern, pageable);
            gratisCount = requisitionRepository.countMonthlyBySalesRepAndType(
                    principal.id(), RequisitionType.GRATIS, period.start(), period.end());
            normalCount = requisitionRepository.countMonthlyBySalesRepAndType(
                    principal.id(), RequisitionType.NORMAL, period.start(), period.end());
        } else if (principal.hasRole("BOOKKEEPER")) {
            var visibleStatuses = workflowPolicy.bookkeeperVisibleStatuses();
            if (status != null && !visibleStatuses.contains(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid requisition status filter");
            }
            result = requisitionRepository.findMonthlyPageByStatuses(
                    visibleStatuses, period.start(), period.end(), null, status,
                    searchEnabled, searchPattern, pageable);
            gratisCount = requisitionRepository.countMonthlyByStatusesAndType(
                    visibleStatuses, RequisitionType.GRATIS, period.start(), period.end());
            normalCount = requisitionRepository.countMonthlyByStatusesAndType(
                    visibleStatuses, RequisitionType.NORMAL, period.start(), period.end());
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User cannot access requisitions");
        }

        return new RequisitionPageResponse(
                gratisCount + normalCount,
                result.getTotalElements(),
                gratisCount,
                normalCount,
                result.getTotalPages(),
                result.getNumber(),
                result.getContent()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public RequisitionSummaryPageResponse getMonthlyCustomerRequisitionPage(
            AppUserPrincipal principal,
            Long customerId,
            int year,
            int month,
            int page
    ) {
        var customer = customerRepository.findById(customerId)
                .filter(item -> item.getStatus() != CustomerStatus.DELETED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (!workflowPolicy.canAccessCustomerRequisitions(customer, principal)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }

        RequisitionPeriod period = periodFactory.monthly(year, month);
        PageRequest pageable = PageRequest.of(
                page,
                REQUISITION_PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<com.ognjen.diplomski.dto.RequisitionListSummaryResponse> result;
        if (principal.hasRole("CEO")) {
            result = requisitionRepository.findMonthlyPage(
                    period.start(), period.end(), customerId, null, false, "", pageable);
        } else if (principal.hasRole("MANAGER")) {
            result = requisitionRepository.findMonthlyPageForManagerTeam(
                    principal.id(), period.start(), period.end(), customerId, null,
                    false, "", pageable);
        } else if (principal.hasRole("SALES_REP")) {
            result = requisitionRepository.findMonthlyPageBySalesRep(
                    principal.id(), period.start(), period.end(), customerId, null,
                    false, "", pageable);
        } else if (principal.hasRole("BOOKKEEPER")) {
            result = requisitionRepository.findMonthlyPageByStatuses(
                    workflowPolicy.bookkeeperVisibleStatuses(),
                    period.start(), period.end(), customerId, null, false, "", pageable);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User cannot access requisitions");
        }
        return new RequisitionSummaryPageResponse(
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getContent()
        );
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        return search.trim().toLowerCase(Locale.ROOT);
    }

    private void synchronizeEditableFields(Requisition requisition, RequisitionRequest request) {
        itemSynchronizer.synchronize(requisition, request.items());
        requisition.setGratisReason(request.gratisReason());
        requisition.setCustomReason(normalizeOptional(request.customReason()));
        requisition.setNote(normalizeOptional(request.note()));
    }

    private Requisition lockForMutation(Long id) {
        try {
            return requisitionRepository.findForUpdate(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requisition not found"));
        } catch (org.springframework.dao.PessimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Requisition is being changed. Reload and try again.", exception);
        }
    }

    private static void requireCurrentRevision(Requisition requisition, String expectedRevision) {
        // Optional for existing API clients; the frontend always sends the revision it loaded.
        if (expectedRevision != null && !expectedRevision.equals(
                com.ognjen.diplomski.domain.requisition.RequisitionRevision.of(requisition))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Requisition has changed. Reload it before saving.");
        }
    }

    private RequisitionResponse toResponse(
            Requisition requisition,
            AppUserPrincipal principal
    ) {
        return RequisitionResponse.from(requisition, workflowPolicy.actionsFor(requisition, principal));
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

}
