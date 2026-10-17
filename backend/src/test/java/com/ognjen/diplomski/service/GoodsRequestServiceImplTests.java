package com.ognjen.diplomski.service;

import com.ognjen.diplomski.repository.AdvertisingMaterialRepository;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.GoodsRequestRepository;
import com.ognjen.diplomski.repository.ProductRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.impl.GoodsRequestServiceImpl;
import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.GoodsRequest;
import com.ognjen.diplomski.entity.GoodsRequestItem;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.enums.GoodsRequestItemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import com.ognjen.diplomski.dto.GoodsRequestItemRequest;
import com.ognjen.diplomski.dto.GoodsRequestRequest;
import java.math.BigDecimal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class GoodsRequestServiceImplTests {

    @Test
    void ceoCannotCreateGoodsRequest() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(repository).create(
                        principal(1L, "ROLE_CEO"),
                        null
                ));

        assertEquals(403, exception.getStatusCode().value());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveAdvertisingMaterialCannotBeAddedToANewGoodsRequest() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        AdvertisingMaterialRepository materials = mock(AdvertisingMaterialRepository.class);
        GoodsRequestServiceImpl service = new GoodsRequestServiceImpl(
                repository, products, materials, mock(AppUserRepository.class));
        when(materials.findAllByIdInAndActiveTrue(any())).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(
                        principal(25L, "ROLE_SALES_REP"),
                        new GoodsRequestRequest(List.of(
                                new GoodsRequestItemRequest(null, 10L, BigDecimal.ONE)
                        ), null)
                ));

        assertEquals(400, exception.getStatusCode().value());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void managerReceivesMonthlyRequestsFromTheirTeam() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        when(repository.findMonthlyCatalogRequestsByManagerTeam(
                eq(7L), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(List.of());

        service(repository).getMonthlyGoodsRequests(principal(7L, "ROLE_MANAGER"), 2026, 9);

        verify(repository).findMonthlyCatalogRequestsByManagerTeam(
                eq(7L), any(OffsetDateTime.class), any(OffsetDateTime.class)
        );
        verify(repository, never()).findMonthlyCatalogRequestsBySalesRep(any(), any(), any());
    }

    @Test
    void ceoReceivesEveryMonthlyRequest() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        AppUser salesRep = new AppUser();
        salesRep.setId(25L); salesRep.setFirstName("Petar"); salesRep.setLastName("Petrović");
        Product product = new Product();
        product.setId(11L); product.setName("Artikal"); product.setActive(false);
        GoodsRequest request = new GoodsRequest();
        request.setId(5L); request.setSalesRep(salesRep); request.setNote("Napomena");
        request.setCreatedAt(OffsetDateTime.parse("2026-09-15T10:00:00+02:00"));
        GoodsRequestItem item = new GoodsRequestItem();
        item.setProduct(product); item.setQuantity(new BigDecimal("3.00"));
        item.setStatus(GoodsRequestItemStatus.APPROVED); request.addItem(item);
        when(repository.findAllMonthlyCatalogRequests(
                any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(List.of(request));

        var response = service(repository).getMonthlyGoodsRequests(
                principal(1L, "ROLE_CEO"), 2026, 9
        );

        assertEquals(1, response.size());
        assertEquals("Petar", response.getFirst().salesRep().firstName());
        assertEquals("Petrović", response.getFirst().salesRep().lastName());
        assertEquals("Artikal", response.getFirst().items().getFirst().name());
        assertEquals(new BigDecimal("3.00"), response.getFirst().items().getFirst().quantity());
        assertEquals("Napomena", response.getFirst().note());
        assertEquals(GoodsRequestItemStatus.APPROVED, response.getFirst().status());
        verify(repository).findAllMonthlyCatalogRequests(
                any(OffsetDateTime.class), any(OffsetDateTime.class)
        );
        verify(repository, never()).findMonthlyCatalogRequestsByManagerTeam(any(), any(), any());
        verify(repository, never()).findMonthlyCatalogRequestsBySalesRep(any(), any(), any());
    }

    @Test
    void salesRepresentativeReceivesOnlyTheirOwnMonthlyRequests() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        when(repository.findMonthlyCatalogRequestsBySalesRep(
                eq(25L), any(OffsetDateTime.class), any(OffsetDateTime.class)
        )).thenReturn(List.of());

        service(repository).getMonthlyGoodsRequests(principal(25L, "ROLE_SALES_REP"), 2026, 9);

        verify(repository).findMonthlyCatalogRequestsBySalesRep(
                eq(25L), any(OffsetDateTime.class), any(OffsetDateTime.class)
        );
        verify(repository, never()).findMonthlyCatalogRequestsByManagerTeam(any(), any(), any());
    }

    @Test
    void bookkeeperReceivesEveryMonthlyRequest() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        when(repository.findAllMonthlyCatalogRequests(any(), any())).thenReturn(List.of());

        service(repository).getMonthlyGoodsRequests(principal(9L, "ROLE_BOOKKEEPER"), 2026, 9);

        verify(repository).findAllMonthlyCatalogRequests(any(), any());
        verify(repository, never()).findMonthlyCatalogRequestsByManagerTeam(any(), any(), any());
        verify(repository, never()).findMonthlyCatalogRequestsBySalesRep(any(), any(), any());
    }

    @Test
    void bookkeeperApprovesAllPendingItems() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        GoodsRequest request = requestWithStatuses(GoodsRequestItemStatus.PENDING, GoodsRequestItemStatus.PENDING);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(repository.saveAndFlush(request)).thenReturn(request);

        var response = service(repository).updateStatus(
                principal(9L, "ROLE_BOOKKEEPER"), 5L, GoodsRequestItemStatus.APPROVED);

        request.getItems().forEach(item -> assertEquals(GoodsRequestItemStatus.APPROVED, item.getStatus()));
        assertEquals(GoodsRequestItemStatus.APPROVED, response.status());
    }

    @Test
    void bookkeeperRejectsAllPendingItems() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        GoodsRequest request = requestWithStatuses(GoodsRequestItemStatus.PENDING);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(repository.saveAndFlush(request)).thenReturn(request);

        var response = service(repository).updateStatus(
                principal(9L, "ROLE_BOOKKEEPER"), 5L, GoodsRequestItemStatus.REJECTED);

        assertEquals(GoodsRequestItemStatus.REJECTED, request.getItems().getFirst().getStatus());
        assertEquals(GoodsRequestItemStatus.REJECTED, response.status());
    }

    @Test
    void nonBookkeepersCannotProcessGoodsRequests() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);

        for (String role : List.of("ROLE_SALES_REP", "ROLE_MANAGER", "ROLE_CEO")) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                    service(repository).updateStatus(principal(1L, role), 5L, GoodsRequestItemStatus.APPROVED));
            assertEquals(403, exception.getStatusCode().value());
        }
        verify(repository, never()).findByIdForUpdate(any());
    }

    @Test
    void pendingCannotBeUsedAsProcessingDecision() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service(repository).updateStatus(
                        principal(9L, "ROLE_BOOKKEEPER"), 5L, GoodsRequestItemStatus.PENDING));

        assertEquals(400, exception.getStatusCode().value());
        verify(repository, never()).findByIdForUpdate(any());
    }

    @Test
    void unknownGoodsRequestCannotBeProcessed() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        when(repository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service(repository).updateStatus(
                        principal(9L, "ROLE_BOOKKEEPER"), 404L, GoodsRequestItemStatus.APPROVED));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void finalizedGoodsRequestCannotBeProcessedAgain() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        GoodsRequest request = requestWithStatuses(GoodsRequestItemStatus.APPROVED);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service(repository).updateStatus(
                        principal(9L, "ROLE_BOOKKEEPER"), 5L, GoodsRequestItemStatus.REJECTED));

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(GoodsRequestItemStatus.APPROVED, request.getItems().getFirst().getStatus());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void mixedRequestOnlyUpdatesPendingItems() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        GoodsRequest request = requestWithStatuses(GoodsRequestItemStatus.APPROVED, GoodsRequestItemStatus.PENDING);
        GoodsRequestItem finalizedItem = request.getItems().getFirst();
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(repository.saveAndFlush(request)).thenReturn(request);

        var response = service(repository).updateStatus(
                principal(9L, "ROLE_BOOKKEEPER"), 5L, GoodsRequestItemStatus.REJECTED);

        assertSame(finalizedItem, request.getItems().getFirst());
        assertEquals(GoodsRequestItemStatus.APPROVED, finalizedItem.getStatus());
        assertEquals(GoodsRequestItemStatus.REJECTED, request.getItems().get(1).getStatus());
        assertEquals(GoodsRequestItemStatus.REJECTED, response.status());
    }

    @Test
    void repeatedProcessingCannotOverwriteFirstDecision() {
        GoodsRequestRepository repository = mock(GoodsRequestRepository.class);
        GoodsRequest request = requestWithStatuses(GoodsRequestItemStatus.PENDING);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(repository.saveAndFlush(request)).thenReturn(request);
        GoodsRequestServiceImpl service = service(repository);

        service.updateStatus(principal(9L, "ROLE_BOOKKEEPER"), 5L, GoodsRequestItemStatus.APPROVED);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                service.updateStatus(principal(9L, "ROLE_BOOKKEEPER"), 5L, GoodsRequestItemStatus.REJECTED));

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(GoodsRequestItemStatus.APPROVED, request.getItems().getFirst().getStatus());
    }

    private static GoodsRequest requestWithStatuses(GoodsRequestItemStatus... statuses) {
        AppUser salesRep = new AppUser();
        salesRep.setId(25L);
        salesRep.setFirstName("Petar");
        salesRep.setLastName("Petrović");
        Product product = new Product();
        product.setId(11L);
        product.setName("Artikal");
        GoodsRequest request = new GoodsRequest();
        request.setId(5L);
        request.setSalesRep(salesRep);
        request.setCreatedAt(OffsetDateTime.parse("2026-09-15T10:00:00+02:00"));
        for (GoodsRequestItemStatus status : statuses) {
            GoodsRequestItem item = new GoodsRequestItem();
            item.setProduct(product);
            item.setQuantity(BigDecimal.ONE);
            item.setStatus(status);
            request.addItem(item);
        }
        return request;
    }

    private static GoodsRequestServiceImpl service(GoodsRequestRepository repository) {
        return new GoodsRequestServiceImpl(
                repository,
                mock(ProductRepository.class),
                mock(AdvertisingMaterialRepository.class),
                mock(AppUserRepository.class)
        );
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
