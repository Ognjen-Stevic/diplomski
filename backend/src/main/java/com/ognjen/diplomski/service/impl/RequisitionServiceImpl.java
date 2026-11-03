package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.dto.MonthlyRequisitionResponse;
import com.ognjen.diplomski.dto.RequisitionActionsResponse;
import com.ognjen.diplomski.dto.RequisitionItemRequest;
import com.ognjen.diplomski.dto.RequisitionEmailRecipient;
import com.ognjen.diplomski.dto.RequisitionRequest;
import com.ognjen.diplomski.dto.RequisitionResponse;
import com.ognjen.diplomski.dto.RequisitionStatusUpdateRequest;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.repository.ProductRepository;
import com.ognjen.diplomski.repository.RequisitionRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.RequisitionService;
import com.ognjen.diplomski.service.RequisitionPdfService;
import com.ognjen.diplomski.service.RequisitionEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequisitionServiceImpl implements RequisitionService {

    private static final Set<RequisitionStatus> BOOKKEEPER_VISIBLE_STATUSES = Set.of(
            RequisitionStatus.APPROVED,
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

    private final RequisitionRepository requisitionRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository appUserRepository;
    private final RequisitionPdfService requisitionPdfService;
    private final RequisitionEmailService requisitionEmailService;

    @Override
    @Transactional
    public RequisitionResponse create(AppUserPrincipal principal, RequisitionRequest request) {
        requireSalesRep(principal);
        validateGratisFields(request);

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
                .filter(item -> canView(item, principal))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requisition not found"));
        return toResponse(requisition, principal);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(AppUserPrincipal principal, Long requisitionId) {
        Requisition requisition = requisitionRepository.findDetailedById(requisitionId)
                .filter(item -> canView(item, principal))
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
                .filter(item -> canView(item, principal))
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
        if (canView(requisition, principal)) requireCurrentRevision(requisition, request.expectedRevision());
        if (!actionsFor(requisition, principal).canEdit()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requisition cannot be edited");
        }
        if (!requisition.getCustomer().getId().equals(request.customerId())
                || requisition.getType() != request.type()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer and requisition type cannot be changed"
            );
        }

        validateGratisFields(request);
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
        RequisitionActionsResponse actions = actionsFor(requisition, principal);
        if (!actions.allowedStatusTransitions().contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Status transition is not allowed");
        }

        requireCurrentRevision(requisition, request.expectedRevision());
        String statusNote = normalizeOptional(request.note());
        if (isRejection(request.status()) && statusNote == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection note is required");
        }

        requisition.setStatus(request.status());
        requisition.setStatusNote(supportsStatusNote(request.status()) ? statusNote : null);
        if (request.status() == RequisitionStatus.SUBMITTED) {
            requisition.setSentAt(OffsetDateTime.now());
        }
        return toResponse(requisitionRepository.saveAndFlush(requisition), principal);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyRequisitionResponse getMonthlyRequisition(AppUserPrincipal principal, int year, int month) {
        YearMonth period = YearMonth.of(year, month);
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime start = period.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = period.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();

        List<Requisition> monthlyRequisitions;
        if (principal.hasRole("CEO")) {
            monthlyRequisitions = requisitionRepository.findAllMonthly(start, end);
        } else if (principal.hasRole("MANAGER")) {
            monthlyRequisitions = requisitionRepository.findMonthlyByManagerTeam(
                    principal.id(),
                    start,
                    end
            );
        } else if (principal.hasRole("SALES_REP")) {
            monthlyRequisitions = requisitionRepository.findMonthlyBySalesRep(
                    principal.id(),
                    start,
                    end
            );
        } else if (principal.hasRole("BOOKKEEPER")) {
            monthlyRequisitions = requisitionRepository.findMonthlyByStatuses(
                    BOOKKEEPER_VISIBLE_STATUSES,
                    start,
                    end
            );
        } else {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User cannot access requisitions"
            );
        }

        List<RequisitionResponse> requisitions = monthlyRequisitions
                .stream()
                .map(requisition -> toResponse(requisition, principal))
                .toList();
        return toMonthlyResponse(requisitions);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyRequisitionResponse getMonthlyCustomerRequisitions(
            AppUserPrincipal principal,
            Long customerId,
            int year,
            int month
    ) {
        var customer = customerRepository.findById(customerId)
                .filter(item -> item.getStatus() != CustomerStatus.DELETED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        boolean canAccess = principal.hasRole("CEO")
                || principal.hasRole("BOOKKEEPER")
                || (principal.hasRole("MANAGER")
                    && customer.getSalesRep().getManager() != null
                    && customer.getSalesRep().getManager().getId().equals(principal.id()))
                || (principal.hasRole("SALES_REP")
                    && customer.getStatus() != CustomerStatus.DEACTIVATED
                    && customer.getSalesRep().getId().equals(principal.id()));
        if (!canAccess) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }

        YearMonth period = YearMonth.of(year, month);
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime start = period.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = period.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();

        List<RequisitionResponse> requisitions = requisitionRepository
                .findMonthlyByCustomer(customerId, start, end)
                .stream()
                .filter(requisition -> canView(requisition, principal))
                .map(requisition -> toResponse(requisition, principal))
                .toList();
        return toMonthlyResponse(requisitions);
    }

    private static MonthlyRequisitionResponse toMonthlyResponse(List<RequisitionResponse> requisitions) {
        long gratisCount = requisitions.stream()
                .filter(requisition -> requisition.type() == RequisitionType.GRATIS)
                .count();

        return new MonthlyRequisitionResponse(
                requisitions.size(),
                gratisCount,
                requisitions.size() - gratisCount,
                requisitions
        );
    }

    private void synchronizeEditableFields(Requisition requisition, RequisitionRequest request) {
        List<String> productCodes = request.items().stream()
                .map(RequisitionItemRequest::productCode)
                .map(String::trim)
                .toList();
        var uniqueCodes = new LinkedHashSet<>(productCodes);
        if (uniqueCodes.size() != productCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product may appear only once");
        }
        Map<String, Product> productsByCode = productRepository
                .findAllByCodeIn(uniqueCodes)
                .stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity()));
        if (productsByCode.size() != uniqueCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more products are unavailable");
        }

        Map<Long, RequisitionItem> existingItemsByProductId = requisition.getItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getProduct().getId(),
                        Function.identity()
                ));
        Set<String> requestedCodes = new HashSet<>(productCodes);
        boolean removesInactiveExistingProduct = requisition.getItems().stream()
                .anyMatch(item -> !item.getProduct().isActive()
                        && !requestedCodes.contains(item.getProduct().getCode()));
        if (removesInactiveExistingProduct) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "An inactive product already in the requisition cannot be removed"
            );
        }

        Set<Long> requestedProductIds = new HashSet<>();
        for (String productCode : productCodes) {
            Product product = productsByCode.get(productCode);
            if (!product.isActive() && !existingItemsByProductId.containsKey(product.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An inactive product cannot be newly added");
            }
            if (!requestedProductIds.add(product.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product may appear only once");
            }
        }

        requisition.setGratisReason(request.gratisReason());
        requisition.setCustomReason(normalizeOptional(request.customReason()));
        requisition.setNote(normalizeOptional(request.note()));
        requisition.getItems().removeIf(
                item -> !requestedProductIds.contains(item.getProduct().getId())
        );

        for (int index = 0; index < request.items().size(); index++) {
            RequisitionItemRequest requestedItem = request.items().get(index);
            Product product = productsByCode.get(productCodes.get(index));
            RequisitionItem item = existingItemsByProductId.get(product.getId());
            if (item == null) {
                item = new RequisitionItem();
                item.setRequisition(requisition);
                item.setProduct(product);
                requisition.getItems().add(item);
            }
            item.setQuantity(requestedItem.quantity());
            item.setStimulationPerUnit(product.getStimulationPerUnit());
        }
    }

    private static boolean canView(Requisition requisition, AppUserPrincipal principal) {
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

    private static RequisitionActionsResponse actionsFor(
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
                com.ognjen.diplomski.service.RequisitionRevision.of(requisition))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Requisition has changed. Reload it before saving.");
        }
    }

    private static boolean isManagerTeamRequisition(Requisition requisition, Long managerId) {
        return requisition.getSalesRep().getManager() != null
                && requisition.getSalesRep().getManager().getId().equals(managerId);
    }

    private static RequisitionResponse toResponse(
            Requisition requisition,
            AppUserPrincipal principal
    ) {
        return RequisitionResponse.from(requisition, actionsFor(requisition, principal));
    }

    private static boolean supportsStatusNote(RequisitionStatus status) {
        return isRejection(status)
                || status == RequisitionStatus.CANCELLED_BY_MANAGER
                || status == RequisitionStatus.CANCELLED_BY_BOOKKEEPER;
    }

    private static boolean isRejection(RequisitionStatus status) {
        return status == RequisitionStatus.REJECTED
                || status == RequisitionStatus.REJECTED_BY_BOOKKEEPER;
    }

    private static void validateGratisFields(RequisitionRequest request) {
        String customReason = normalizeOptional(request.customReason());
        if (request.type() == RequisitionType.NORMAL) {
            if (request.gratisReason() != null || customReason != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Normal requisitions cannot have a gratis reason");
            }
            return;
        }
        if (request.gratisReason() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gratis reason is required");
        }
        if (request.gratisReason() == GratisReason.OTHER && customReason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Custom reason is required for OTHER");
        }
        if (request.gratisReason() != GratisReason.OTHER && customReason != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Custom reason is allowed only for OTHER");
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void requireSalesRep(AppUserPrincipal principal) {
        if (!principal.hasRole("SALES_REP")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sales representatives can access requisitions");
        }
    }

}
