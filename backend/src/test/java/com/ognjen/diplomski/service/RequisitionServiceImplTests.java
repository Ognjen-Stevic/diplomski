package com.ognjen.diplomski.service;

import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import com.ognjen.diplomski.dto.RequisitionItemRequest;
import com.ognjen.diplomski.dto.RequisitionEmailRecipient;
import com.ognjen.diplomski.dto.RequisitionRequest;
import com.ognjen.diplomski.dto.RequisitionStatusUpdateRequest;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.repository.ProductRepository;
import com.ognjen.diplomski.repository.RequisitionRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.impl.RequisitionServiceImpl;
import com.ognjen.diplomski.service.RequisitionPdfService;
import com.ognjen.diplomski.service.RequisitionEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RequisitionServiceImplTests {

    @Test
    void managerReceivesNonDraftTeamRequisitions() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionServiceImpl service = service(repository);
        Requisition submitted = requisition(1L, RequisitionStatus.SUBMITTED);
        Requisition approved = requisition(2L, RequisitionStatus.APPROVED);
        when(repository.findMonthlyByManagerTeam(
                eq(7L), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(List.of(submitted, approved));

        var response = service.getMonthlyRequisition(
                principal(7L, "ROLE_MANAGER"),
                2026,
                9
        );

        assertEquals(2, response.totalCount());
        assertEquals(RequisitionStatus.SUBMITTED, response.requisitions().get(0).status());
        assertEquals(RequisitionStatus.APPROVED, response.requisitions().get(1).status());
        assertEquals("Sales", response.requisitions().get(0).salesRep().firstName());
        verify(repository).findMonthlyByManagerTeam(
                eq(7L), any(OffsetDateTime.class), any(OffsetDateTime.class)
        );
    }

    @Test
    void ceoReceivesAllMonthlyRequisitions() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionServiceImpl service = service(repository);
        when(repository.findAllMonthly(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        service.getMonthlyRequisition(principal(1L, "ROLE_CEO"), 2026, 9);

        verify(repository).findAllMonthly(any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    @Test
    void salesRepresentativeReceivesOnlyOwnMonthlyRequisitions() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionServiceImpl service = service(repository);
        when(repository.findMonthlyBySalesRep(
                eq(20L), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(List.of());

        service.getMonthlyRequisition(principal(20L, "ROLE_SALES_REP"), 2026, 9);

        verify(repository).findMonthlyBySalesRep(
                eq(20L), any(OffsetDateTime.class), any(OffsetDateTime.class)
        );
    }

    @Test
    void unsupportedRoleCannotFetchMonthlyRequisitions() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionServiceImpl service = service(repository);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getMonthlyRequisition(principal(30L, "ROLE_USER"), 2026, 9)
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void managerCannotApproveDraftRequisition() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionServiceImpl service = service(repository);
        Requisition draft = managerTeamRequisition(7L, RequisitionStatus.DRAFT);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(draft));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateStatus(
                        principal(7L, "ROLE_MANAGER"),
                        1L,
                        new RequisitionStatusUpdateRequest(RequisitionStatus.APPROVED, null)
                )
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void completedRequisitionPdfIsAvailableToEveryUserWhoCanViewIt() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionPdfService pdfService = mock(RequisitionPdfService.class);
        Requisition completed = managerTeamRequisition(7L, RequisitionStatus.COMPLETED);
        byte[] expectedPdf = {1, 2, 3};
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(completed));
        when(pdfService.createPdf(completed)).thenReturn(expectedPdf);
        RequisitionServiceImpl service = service(repository, mock(ProductRepository.class), pdfService);

        List<AppUserPrincipal> viewers = List.of(
                principal(20L, "ROLE_SALES_REP"),
                principal(7L, "ROLE_MANAGER"),
                principal(30L, "ROLE_BOOKKEEPER"),
                principal(1L, "ROLE_CEO")
        );

        for (AppUserPrincipal viewer : viewers) {
            assertSame(expectedPdf, service.generatePdf(viewer, 1L));
        }
        verify(pdfService, times(viewers.size())).createPdf(completed);
    }

    @Test
    void requisitionPdfRequiresViewAccessAndCompletedStatus() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionPdfService pdfService = mock(RequisitionPdfService.class);
        Requisition approved = managerTeamRequisition(7L, RequisitionStatus.APPROVED);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(approved));
        RequisitionServiceImpl service = service(repository, mock(ProductRepository.class), pdfService);

        ResponseStatusException unavailable = assertThrows(
                ResponseStatusException.class,
                () -> service.generatePdf(principal(20L, "ROLE_SALES_REP"), 1L)
        );
        ResponseStatusException hidden = assertThrows(
                ResponseStatusException.class,
                () -> service.generatePdf(principal(99L, "ROLE_SALES_REP"), 1L)
        );

        assertEquals(409, unavailable.getStatusCode().value());
        assertEquals(404, hidden.getStatusCode().value());
        verifyNoInteractions(pdfService);
    }

    @Test
    void bookkeeperCanEmailCompletedPdfToBackendResolvedRecipients() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionPdfService pdfService = mock(RequisitionPdfService.class);
        RequisitionEmailService emailService = mock(RequisitionEmailService.class);
        Requisition completed = requisition(1L, RequisitionStatus.COMPLETED);
        byte[] pdf = {1, 2, 3};
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(completed));
        when(pdfService.createPdf(completed)).thenReturn(pdf);
        RequisitionServiceImpl service = service(
                repository,
                mock(ProductRepository.class),
                pdfService,
                emailService
        );

        service.sendPdfByEmail(
                principal(30L, "ROLE_BOOKKEEPER"),
                1L,
                RequisitionEmailRecipient.SALES_REP
        );
        service.sendPdfByEmail(
                principal(30L, "ROLE_BOOKKEEPER"),
                1L,
                RequisitionEmailRecipient.CUSTOMER
        );

        verify(emailService).sendPdf(completed, "sales@example.com", pdf);
        verify(emailService).sendPdf(completed, "customer@example.com", pdf);
        verify(pdfService, times(2)).createPdf(completed);
    }

    @Test
    void emailActionRequiresBookkeeperCompletedStatusAndRecipientEmail() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionPdfService pdfService = mock(RequisitionPdfService.class);
        RequisitionEmailService emailService = mock(RequisitionEmailService.class);
        Requisition approved = requisition(1L, RequisitionStatus.APPROVED);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(approved));
        RequisitionServiceImpl service = service(
                repository,
                mock(ProductRepository.class),
                pdfService,
                emailService
        );

        ResponseStatusException forbidden = assertThrows(
                ResponseStatusException.class,
                () -> service.sendPdfByEmail(
                        principal(20L, "ROLE_SALES_REP"),
                        1L,
                        RequisitionEmailRecipient.SALES_REP
                )
        );
        ResponseStatusException incomplete = assertThrows(
                ResponseStatusException.class,
                () -> service.sendPdfByEmail(
                        principal(30L, "ROLE_BOOKKEEPER"),
                        1L,
                        RequisitionEmailRecipient.SALES_REP
                )
        );

        approved.setStatus(RequisitionStatus.COMPLETED);
        approved.getCustomer().setEmail(null);
        ResponseStatusException missingEmail = assertThrows(
                ResponseStatusException.class,
                () -> service.sendPdfByEmail(
                        principal(30L, "ROLE_BOOKKEEPER"),
                        1L,
                        RequisitionEmailRecipient.CUSTOMER
                )
        );

        assertEquals(403, forbidden.getStatusCode().value());
        assertEquals(409, incomplete.getStatusCode().value());
        assertEquals(409, missingEmail.getStatusCode().value());
        verifyNoInteractions(pdfService, emailService);
    }

    @Test
    void managerMustProvideNoteWhenRejectingSubmittedRequisition() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionServiceImpl service = service(repository);
        Requisition submitted = managerTeamRequisition(7L, RequisitionStatus.SUBMITTED);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(submitted));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateStatus(
                        principal(7L, "ROLE_MANAGER"),
                        1L,
                        new RequisitionStatusUpdateRequest(RequisitionStatus.REJECTED, " ")
                )
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void bookkeeperReceivesExpectedStatusOptions() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionServiceImpl service = service(repository);
        Requisition approved = requisition(1L, RequisitionStatus.APPROVED);
        Requisition processing = requisition(2L, RequisitionStatus.PROCESSING);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(approved));
        when(repository.findDetailedById(2L)).thenReturn(Optional.of(processing));

        var approvedResponse = service.findById(principal(30L, "ROLE_BOOKKEEPER"), 1L);
        var processingResponse = service.findById(principal(30L, "ROLE_BOOKKEEPER"), 2L);

        assertEquals(
                List.of(
                        RequisitionStatus.COMPLETED,
                        RequisitionStatus.CANCELLED_BY_BOOKKEEPER,
                        RequisitionStatus.REJECTED_BY_BOOKKEEPER,
                        RequisitionStatus.PROCESSING
                ),
                approvedResponse.actions().allowedStatusTransitions()
        );
        assertEquals(
                List.of(
                        RequisitionStatus.COMPLETED,
                        RequisitionStatus.CANCELLED_BY_BOOKKEEPER,
                        RequisitionStatus.REJECTED_BY_BOOKKEEPER
                ),
                processingResponse.actions().allowedStatusTransitions()
        );
    }

    @Test
    void bookkeeperCannotUseAnyOtherTransitionFromProcessing() {
        List<RequisitionStatus> disallowedStatuses = List.of(
                RequisitionStatus.DRAFT,
                RequisitionStatus.SUBMITTED,
                RequisitionStatus.APPROVED,
                RequisitionStatus.REJECTED,
                RequisitionStatus.PROCESSING,
                RequisitionStatus.CANCELLED_BY_MANAGER
        );

        for (RequisitionStatus requestedStatus : disallowedStatuses) {
            RequisitionRepository repository = mock(RequisitionRepository.class);
            RequisitionServiceImpl service = service(repository);
            Requisition processing = requisition(1L, RequisitionStatus.PROCESSING);
            when(repository.findDetailedById(1L)).thenReturn(Optional.of(processing));

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service.updateStatus(
                            principal(30L, "ROLE_BOOKKEEPER"),
                            1L,
                            new RequisitionStatusUpdateRequest(requestedStatus, "Reason")
                    )
            );

            assertEquals(409, exception.getStatusCode().value());
            verify(repository, never()).saveAndFlush(any());
        }
    }

    @Test
    void bookkeeperCanFinishApprovedRequisitionWithoutProcessingStep() {
        for (RequisitionStatus terminalStatus : List.of(
                RequisitionStatus.COMPLETED,
                RequisitionStatus.CANCELLED_BY_BOOKKEEPER
        )) {
            RequisitionRepository repository = mock(RequisitionRepository.class);
            RequisitionServiceImpl service = service(repository);
            Requisition approved = requisition(1L, RequisitionStatus.APPROVED);
            when(repository.findDetailedById(1L)).thenReturn(Optional.of(approved));
            when(repository.saveAndFlush(approved)).thenReturn(approved);

            var response = service.updateStatus(
                    principal(30L, "ROLE_BOOKKEEPER"),
                    1L,
                    new RequisitionStatusUpdateRequest(terminalStatus, "Finished")
            );

            assertEquals(terminalStatus, response.status());
            assertTrue(response.actions().allowedStatusTransitions().isEmpty());
            verify(repository).saveAndFlush(approved);
        }
    }

    @Test
    void bookkeeperRejectionReturnsRequisitionToManagerForReview() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionServiceImpl service = service(repository);
        Requisition processing = managerTeamRequisition(7L, RequisitionStatus.PROCESSING);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(processing));
        when(repository.saveAndFlush(processing)).thenReturn(processing);

        service.updateStatus(
                principal(30L, "ROLE_BOOKKEEPER"),
                1L,
                new RequisitionStatusUpdateRequest(
                        RequisitionStatus.REJECTED_BY_BOOKKEEPER,
                        "  Review documentation  "
                )
        );
        var managerResponse = service.findById(principal(7L, "ROLE_MANAGER"), 1L);

        assertEquals(RequisitionStatus.REJECTED_BY_BOOKKEEPER, managerResponse.status());
        assertEquals("Review documentation", managerResponse.statusNote());
        assertTrue(managerResponse.actions().canEdit());
        assertEquals(
                List.of(RequisitionStatus.APPROVED, RequisitionStatus.CANCELLED_BY_MANAGER),
                managerResponse.actions().allowedStatusTransitions()
        );
        verify(repository).saveAndFlush(processing);
    }

    @Test
    void bookkeeperCanCompleteProcessingRequisition() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionServiceImpl service = service(repository);
        Requisition processing = requisition(1L, RequisitionStatus.PROCESSING);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(processing));
        when(repository.saveAndFlush(processing)).thenReturn(processing);

        var response = service.updateStatus(
                principal(30L, "ROLE_BOOKKEEPER"),
                1L,
                new RequisitionStatusUpdateRequest(RequisitionStatus.COMPLETED, "ignored")
        );

        assertEquals(RequisitionStatus.COMPLETED, processing.getStatus());
        assertNull(processing.getStatusNote());
        assertEquals(RequisitionStatus.COMPLETED, response.status());
        assertTrue(response.actions().allowedStatusTransitions().isEmpty());
        verify(repository).saveAndFlush(processing);
    }

    @Test
    void bookkeeperCanCancelProcessingRequisition() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        RequisitionServiceImpl service = service(repository);
        Requisition processing = requisition(1L, RequisitionStatus.PROCESSING);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(processing));
        when(repository.saveAndFlush(processing)).thenReturn(processing);

        var response = service.updateStatus(
                principal(30L, "ROLE_BOOKKEEPER"),
                1L,
                new RequisitionStatusUpdateRequest(
                        RequisitionStatus.CANCELLED_BY_BOOKKEEPER,
                        "  Supplier cancelled  "
                )
        );

        assertEquals(RequisitionStatus.CANCELLED_BY_BOOKKEEPER, processing.getStatus());
        assertEquals("Supplier cancelled", processing.getStatusNote());
        assertEquals(RequisitionStatus.CANCELLED_BY_BOOKKEEPER, response.status());
        assertEquals("Supplier cancelled", response.statusNote());
        assertTrue(response.actions().allowedStatusTransitions().isEmpty());
        verify(repository).saveAndFlush(processing);
    }

    @Test
    void updateChangesExistingItemWithoutReplacingIt() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = requisition(1L, RequisitionStatus.DRAFT);
        Product product = product(11L, "P-11", "1.50");
        RequisitionItem existingItem = item(101L, requisition, product, "2.00");
        requisition.getItems().add(existingItem);
        prepareUpdate(repository, productRepository, requisition, List.of(product));

        service(repository, productRepository).update(
                principal(20L, "ROLE_SALES_REP"),
                1L,
                request(new RequisitionItemRequest("P-11", new BigDecimal("7.00")))
        );

        assertEquals(1, requisition.getItems().size());
        assertSame(existingItem, requisition.getItems().getFirst());
        assertEquals(101L, requisition.getItems().getFirst().getId());
        assertEquals(new BigDecimal("7.00"), existingItem.getQuantity());
        assertEquals(new BigDecimal("1.50"), existingItem.getStimulationPerUnit());
        verify(repository).flush();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateRemovesItemMissingFromRequest() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = requisition(1L, RequisitionStatus.DRAFT);
        Product retainedProduct = product(11L, "P-11", "1.00");
        Product removedProduct = product(12L, "P-12", "2.00");
        RequisitionItem retainedItem = item(101L, requisition, retainedProduct, "2.00");
        requisition.getItems().add(retainedItem);
        requisition.getItems().add(item(102L, requisition, removedProduct, "3.00"));
        prepareUpdate(repository, productRepository, requisition, List.of(retainedProduct));

        service(repository, productRepository).update(
                principal(20L, "ROLE_SALES_REP"),
                1L,
                request(new RequisitionItemRequest("P-11", new BigDecimal("2.00")))
        );

        assertEquals(1, requisition.getItems().size());
        assertSame(retainedItem, requisition.getItems().getFirst());
        verify(repository).flush();
    }

    @Test
    void updateAddsOnlyNewProductItem() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = requisition(1L, RequisitionStatus.DRAFT);
        Product existingProduct = product(11L, "P-11", "1.00");
        Product newProduct = product(12L, "P-12", "2.00");
        RequisitionItem existingItem = item(101L, requisition, existingProduct, "2.00");
        requisition.getItems().add(existingItem);
        prepareUpdate(repository, productRepository, requisition, List.of(existingProduct, newProduct));

        service(repository, productRepository).update(
                principal(20L, "ROLE_SALES_REP"),
                1L,
                request(
                        new RequisitionItemRequest("P-11", new BigDecimal("2.00")),
                        new RequisitionItemRequest("P-12", new BigDecimal("4.00"))
                )
        );

        assertEquals(2, requisition.getItems().size());
        assertSame(existingItem, requisition.getItems().getFirst());
        RequisitionItem addedItem = requisition.getItems().get(1);
        assertNull(addedItem.getId());
        assertSame(requisition, addedItem.getRequisition());
        assertSame(newProduct, addedItem.getProduct());
        assertEquals(new BigDecimal("4.00"), addedItem.getQuantity());
        assertEquals(2, requisition.getItems().stream()
                .map(item -> item.getProduct().getId())
                .distinct()
                .count());
        verify(repository).flush();
    }

    @Test
    void updateKeepsUnchangedExistingItem() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = requisition(1L, RequisitionStatus.DRAFT);
        Product product = product(11L, "P-11", "1.00");
        RequisitionItem existingItem = item(101L, requisition, product, "2.00");
        requisition.getItems().add(existingItem);
        prepareUpdate(repository, productRepository, requisition, List.of(product));

        service(repository, productRepository).update(
                principal(20L, "ROLE_SALES_REP"),
                1L,
                request(new RequisitionItemRequest("P-11", new BigDecimal("2.00")))
        );

        assertEquals(1, requisition.getItems().size());
        assertSame(existingItem, requisition.getItems().getFirst());
        assertEquals(101L, existingItem.getId());
        verify(repository).flush();
    }

    @Test
    void updateAllowsAnInactiveProductAlreadyInTheRequisitionToRemain() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = requisition(1L, RequisitionStatus.DRAFT);
        Product product = product(11L, "P-11", "1.00");
        product.setActive(false);
        RequisitionItem existingItem = item(101L, requisition, product, "2.00");
        requisition.getItems().add(existingItem);
        prepareUpdate(repository, productRepository, requisition, List.of(product));

        service(repository, productRepository).update(
                principal(20L, "ROLE_SALES_REP"),
                1L,
                request(new RequisitionItemRequest("P-11", new BigDecimal("3.00")))
        );

        assertSame(existingItem, requisition.getItems().getFirst());
        assertEquals(new BigDecimal("3.00"), existingItem.getQuantity());
        verify(repository).flush();
    }

    @Test
    void updateRejectsNewlyAddingAnInactiveProduct() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = requisition(1L, RequisitionStatus.DRAFT);
        Product product = product(11L, "P-11", "1.00");
        product.setActive(false);
        prepareUpdate(repository, productRepository, requisition, List.of(product));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(repository, productRepository).update(
                        principal(20L, "ROLE_SALES_REP"),
                        1L,
                        request(new RequisitionItemRequest("P-11", BigDecimal.ONE))
                ));

        assertEquals(400, exception.getStatusCode().value());
        verify(repository, never()).flush();
    }

    @Test
    void updateRejectsRemovingAnExistingInactiveProduct() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = requisition(1L, RequisitionStatus.DRAFT);
        Product inactiveProduct = product(11L, "P-11", "1.00");
        inactiveProduct.setActive(false);
        Product activeProduct = product(12L, "P-12", "1.00");
        requisition.getItems().add(item(101L, requisition, inactiveProduct, "2.00"));
        requisition.getItems().add(item(102L, requisition, activeProduct, "2.00"));
        prepareUpdate(repository, productRepository, requisition, List.of(activeProduct));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(repository, productRepository).update(
                        principal(20L, "ROLE_SALES_REP"),
                        1L,
                        request(new RequisitionItemRequest("P-12", BigDecimal.ONE))
                ));

        assertEquals(400, exception.getStatusCode().value());
        assertEquals(2, requisition.getItems().size());
    }

    @Test
    void updateRejectsDuplicateProductCodesBeforeSaving() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = requisition(1L, RequisitionStatus.DRAFT);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(requisition));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service(repository, productRepository).update(
                        principal(20L, "ROLE_SALES_REP"),
                        1L,
                        request(
                                new RequisitionItemRequest("P-11", BigDecimal.ONE),
                                new RequisitionItemRequest("P-11", BigDecimal.TWO)
                        )
                )
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(productRepository);
        verify(repository, never()).flush();
    }

    @Test
    void updateRejectsDifferentCodesResolvingToSameProductId() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = requisition(1L, RequisitionStatus.DRAFT);
        Product firstAlias = product(11L, "P-11", "1.00");
        Product secondAlias = product(11L, "P-11-ALIAS", "1.00");
        prepareUpdate(repository, productRepository, requisition, List.of(firstAlias, secondAlias));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service(repository, productRepository).update(
                        principal(20L, "ROLE_SALES_REP"),
                        1L,
                        request(
                                new RequisitionItemRequest("P-11", BigDecimal.ONE),
                                new RequisitionItemRequest("P-11-ALIAS", BigDecimal.TWO)
                        )
                )
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(repository, never()).flush();
    }

    @Test
    void managerUpdatesSubmittedRequisitionFromOwnTeam() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = managerTeamRequisition(7L, RequisitionStatus.SUBMITTED);
        Product product = product(11L, "P-11", "1.50");
        RequisitionItem existingItem = item(101L, requisition, product, "2.00");
        requisition.getItems().add(existingItem);
        prepareUpdate(repository, productRepository, requisition, List.of(product));

        var response = service(repository, productRepository).update(
                principal(7L, "ROLE_MANAGER"),
                1L,
                request(new RequisitionItemRequest("P-11", new BigDecimal("6.00")))
        );

        assertSame(existingItem, requisition.getItems().getFirst());
        assertEquals(new BigDecimal("6.00"), existingItem.getQuantity());
        assertTrue(response.actions().canEdit());
        verify(repository).flush();
    }

    @Test
    void managerCannotUpdateRequisitionFromAnotherTeam() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = managerTeamRequisition(99L, RequisitionStatus.SUBMITTED);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(requisition));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service(repository, productRepository).update(
                        principal(7L, "ROLE_MANAGER"),
                        1L,
                        request(new RequisitionItemRequest("P-11", BigDecimal.ONE))
                )
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(productRepository);
        verify(repository, never()).flush();
    }

    @Test
    void managerCannotUpdateRequisitionAfterItIsForwardedToBookkeeping() {
        RequisitionRepository repository = mock(RequisitionRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        Requisition requisition = managerTeamRequisition(7L, RequisitionStatus.APPROVED);
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(requisition));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service(repository, productRepository).update(
                        principal(7L, "ROLE_MANAGER"),
                        1L,
                        request(new RequisitionItemRequest("P-11", BigDecimal.ONE))
                )
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(productRepository);
        verify(repository, never()).flush();
    }

    private static RequisitionServiceImpl service(RequisitionRepository repository) {
        return service(repository, mock(ProductRepository.class));
    }

    private static RequisitionServiceImpl service(
            RequisitionRepository repository,
            ProductRepository productRepository
    ) {
        return service(repository, productRepository, mock(RequisitionPdfService.class));
    }

    private static RequisitionServiceImpl service(
            RequisitionRepository repository,
            ProductRepository productRepository,
            RequisitionPdfService pdfService
    ) {
        return service(repository, productRepository, pdfService, mock(RequisitionEmailService.class));
    }

    private static RequisitionServiceImpl service(
            RequisitionRepository repository,
            ProductRepository productRepository,
            RequisitionPdfService pdfService,
            RequisitionEmailService emailService
    ) {
        return new RequisitionServiceImpl(
                repository,
                mock(CustomerRepository.class),
                productRepository,
                mock(AppUserRepository.class),
                pdfService,
                emailService
        );
    }

    private static void prepareUpdate(
            RequisitionRepository repository,
            ProductRepository productRepository,
            Requisition requisition,
            List<Product> products
    ) {
        when(repository.findDetailedById(1L)).thenReturn(Optional.of(requisition));
        when(productRepository.findAllByCodeIn(any())).thenReturn(products);
    }

    private static RequisitionRequest request(RequisitionItemRequest... items) {
        return new RequisitionRequest(
                100L,
                RequisitionType.NORMAL,
                null,
                null,
                null,
                List.of(items)
        );
    }

    private static Product product(Long id, String code, String stimulation) {
        Product product = new Product();
        product.setId(id);
        product.setCode(code);
        product.setName(code);
        product.setStimulationPerUnit(new BigDecimal(stimulation));
        return product;
    }

    private static RequisitionItem item(
            Long id,
            Requisition requisition,
            Product product,
            String quantity
    ) {
        RequisitionItem item = new RequisitionItem();
        item.setId(id);
        item.setRequisition(requisition);
        item.setProduct(product);
        item.setQuantity(new BigDecimal(quantity));
        item.setStimulationPerUnit(product.getStimulationPerUnit());
        return item;
    }

    private static Requisition requisition(Long id, RequisitionStatus status) {
        AppUser salesRepresentative = new AppUser();
        salesRepresentative.setId(20L);
        salesRepresentative.setFirstName("Sales");
        salesRepresentative.setLastName("Representative");
        salesRepresentative.setEmail("sales@example.com");

        Customer customer = new Customer();
        customer.setId(100L);
        customer.setObjectName("Object");
        customer.setCompanyName("Company");
        customer.setEmail("customer@example.com");

        Requisition requisition = new Requisition();
        requisition.setId(id);
        requisition.setSalesRep(salesRepresentative);
        requisition.setCustomer(customer);
        requisition.setType(RequisitionType.NORMAL);
        requisition.setCreatedAt(OffsetDateTime.now());
        requisition.setStatus(status);
        return requisition;
    }

    private static Requisition managerTeamRequisition(Long managerId, RequisitionStatus status) {
        Requisition requisition = requisition(1L, status);
        AppUser manager = new AppUser();
        manager.setId(managerId);
        requisition.getSalesRep().setManager(manager);
        return requisition;
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
