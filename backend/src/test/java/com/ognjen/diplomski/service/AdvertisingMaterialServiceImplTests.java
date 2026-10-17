package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.AdvertisingMaterialActiveRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialCreateRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialDistributionItemRequest;
import com.ognjen.diplomski.dto.CreateAdvertisingMaterialDistributionRequest;
import com.ognjen.diplomski.entity.AdvertisingMaterial;
import com.ognjen.diplomski.entity.AdvertisingMaterialDistribution;
import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.repository.AdvertisingMaterialDistributionRepository;
import com.ognjen.diplomski.repository.AdvertisingMaterialRepository;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.impl.AdvertisingMaterialServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdvertisingMaterialServiceImplTests {

    private AdvertisingMaterialRepository materials;
    private AdvertisingMaterialDistributionRepository distributions;
    private CustomerRepository customers;
    private AppUserRepository users;
    private AdvertisingMaterialServiceImpl service;

    @BeforeEach
    void setUp() {
        materials = mock(AdvertisingMaterialRepository.class);
        distributions = mock(AdvertisingMaterialDistributionRepository.class);
        customers = mock(CustomerRepository.class);
        users = mock(AppUserRepository.class);
        service = new AdvertisingMaterialServiceImpl(materials, distributions, customers, users);
        when(materials.saveAndFlush(any(AdvertisingMaterial.class))).thenAnswer(invocation -> {
            AdvertisingMaterial material = invocation.getArgument(0);
            if (material.getId() == null) material.setId(10L);
            return material;
        });
    }

    @Test
    void ceoCreatesAnActiveMaterialWithUniqueTrimmedName() {
        var response = service.createAdvertisingMaterial(
                ceo(), new AdvertisingMaterialCreateRequest("  Promo stalak  "));

        assertEquals("Promo stalak", response.name());
        assertTrue(response.active());
        verify(materials).existsByNameIgnoreCase("Promo stalak");
    }

    @Test
    void duplicateNameIsRejectedCleanlyBeforeOrDuringSave() {
        when(materials.existsByNameIgnoreCase("Promo")).thenReturn(true);
        ResponseStatusException existing = assertThrows(ResponseStatusException.class,
                () -> service.createAdvertisingMaterial(
                        ceo(), new AdvertisingMaterialCreateRequest("Promo")));
        assertEquals(409, existing.getStatusCode().value());

        when(materials.existsByNameIgnoreCase("Drugi")).thenReturn(false);
        when(materials.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("raw sql"));
        ResponseStatusException race = assertThrows(ResponseStatusException.class,
                () -> service.createAdvertisingMaterial(
                        ceo(), new AdvertisingMaterialCreateRequest("Drugi")));
        assertEquals(409, race.getStatusCode().value());
        assertFalse(race.getReason().contains("sql"));
    }

    @Test
    void ceoCanDeactivateAndReactivateMaterial() {
        AdvertisingMaterial material = material(true);
        when(materials.findById(10L)).thenReturn(Optional.of(material));

        assertFalse(service.setAdvertisingMaterialActive(
                ceo(), 10L, new AdvertisingMaterialActiveRequest(false)).active());
        assertTrue(service.setAdvertisingMaterialActive(
                ceo(), 10L, new AdvertisingMaterialActiveRequest(true)).active());
        verify(materials, times(2)).saveAndFlush(material);
    }

    @Test
    void requisitionAvailabilityContainsOnlyActiveMaterialsWhileCeoListContainsBoth() {
        AdvertisingMaterial active = material(true);
        AdvertisingMaterial inactive = new AdvertisingMaterial();
        inactive.setId(11L); inactive.setName("Stari promo"); inactive.setActive(false);
        when(materials.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(active));
        when(materials.findAllByOrderByNameAsc()).thenReturn(List.of(active, inactive));

        var available = service.getAvailableAdvertisingMaterials();
        var managed = service.getAdvertisingMaterials(ceo());

        assertEquals(List.of("Promo"), available.stream().map(item -> item.name()).toList());
        assertEquals(2, managed.size());
        assertTrue(managed.getFirst().active());
        assertFalse(managed.getLast().active());
    }

    @Test
    void nonCeoCannotUseAdministrationOperations() {
        AppUserPrincipal salesRep = principal(20L, "ROLE_SALES_REP");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.getAdvertisingMaterials(salesRep)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.createAdvertisingMaterial(salesRep, null)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.setAdvertisingMaterialActive(salesRep, 10L, null)).getStatusCode().value());
        verifyNoInteractions(materials, distributions, customers, users);
    }

    @Test
    void inactiveMaterialCannotBeNewlyAssignedEvenWithManualRequest() {
        Customer customer = new Customer(); customer.setId(5L);
        when(customers.findByIdAndSalesRepIdAndStatus(5L, 20L, CustomerStatus.APPROVED))
                .thenReturn(Optional.of(customer));
        when(materials.findAllByIdInAndActiveTrue(any())).thenReturn(List.of());
        CreateAdvertisingMaterialDistributionRequest request =
                new CreateAdvertisingMaterialDistributionRequest(
                5L,
                List.of(new AdvertisingMaterialDistributionItemRequest(10L, BigDecimal.ONE)),
                null
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.recordAdvertisingMaterialDistribution(
                        principal(20L, "ROLE_SALES_REP"), request));

        assertEquals(400, exception.getStatusCode().value());
        verify(distributions, never()).saveAllAndFlush(any());
    }

    @Test
    void ceoCannotAssignAdvertisingMaterial() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.recordAdvertisingMaterialDistribution(ceo(), null));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(materials, distributions, customers, users);
    }

    @Test
    void inactiveMaterialRemainsVisibleInHistoricalDistributionData() {
        AdvertisingMaterial material = material(false);
        Customer customer = new Customer();
        customer.setId(5L); customer.setObjectName("Objekat"); customer.setCompanyName("Firma");
        AdvertisingMaterialDistribution distribution = new AdvertisingMaterialDistribution();
        distribution.setId(30L); distribution.setMaterial(material); distribution.setCustomer(customer);
        distribution.setSalesRep(salesRep());
        distribution.setQuantity(BigDecimal.TWO); distribution.setDistributedAt(OffsetDateTime.now());
        when(distributions.findMonthlyBySalesRep(eq(20L), any(), any()))
                .thenReturn(List.of(distribution));

        var response = service.getMonthlyAdvertisingMaterials(
                principal(20L, "ROLE_SALES_REP"), 2026, 10);

        assertEquals(1, response.size());
        assertEquals("Promo", response.getFirst().materialName());
        assertEquals(BigDecimal.TWO, response.getFirst().quantity());
    }

    @Test
    void ceoReceivesEveryMonthlyDistributionWithSalesRepresentativeDetails() {
        AdvertisingMaterialDistribution distribution = new AdvertisingMaterialDistribution();
        distribution.setId(31L); distribution.setMaterial(material(false)); distribution.setSalesRep(salesRep());
        Customer customer = new Customer();
        customer.setId(5L); customer.setObjectName("Objekat"); customer.setCompanyName("Firma");
        distribution.setCustomer(customer); distribution.setQuantity(new BigDecimal("4.00"));
        distribution.setNote("Napomena"); distribution.setDistributedAt(OffsetDateTime.now());
        when(distributions.findAllMonthly(any(), any())).thenReturn(List.of(distribution));

        var response = service.getMonthlyAdvertisingMaterials(ceo(), 2026, 10);

        assertEquals(1, response.size());
        assertEquals("Petar", response.getFirst().salesRep().firstName());
        assertEquals("Petrović", response.getFirst().salesRep().lastName());
        assertEquals("Promo", response.getFirst().materialName());
        assertEquals(new BigDecimal("4.00"), response.getFirst().quantity());
        assertEquals("Napomena", response.getFirst().note());
        verify(distributions).findAllMonthly(any(), any());
        verify(distributions, never()).findMonthlyBySalesRep(any(), any(), any());
    }

    @Test
    void managerReceivesMonthlyDistributionsFromTheirOwnTeam() {
        AdvertisingMaterialDistribution distribution = new AdvertisingMaterialDistribution();
        distribution.setId(32L); distribution.setMaterial(material(true)); distribution.setSalesRep(salesRep());
        Customer customer = new Customer();
        customer.setId(5L); customer.setObjectName("Objekat"); customer.setCompanyName("Firma");
        distribution.setCustomer(customer); distribution.setQuantity(BigDecimal.ONE);
        distribution.setDistributedAt(OffsetDateTime.now());
        when(distributions.findMonthlyByManagerTeam(eq(8L), any(), any()))
                .thenReturn(List.of(distribution));

        var response = service.getMonthlyAdvertisingMaterials(
                principal(8L, "ROLE_MANAGER"), 2026, 10
        );

        assertEquals(1, response.size());
        assertEquals("Petar", response.getFirst().salesRep().firstName());
        verify(distributions).findMonthlyByManagerTeam(eq(8L), any(), any());
        verify(distributions, never()).findAllMonthly(any(), any());
        verify(distributions, never()).findMonthlyBySalesRep(any(), any(), any());
    }

    private static AdvertisingMaterial material(boolean active) {
        AdvertisingMaterial material = new AdvertisingMaterial();
        material.setId(10L); material.setName("Promo"); material.setActive(active);
        return material;
    }

    private static AppUser salesRep() {
        AppUser salesRep = new AppUser();
        salesRep.setId(20L); salesRep.setFirstName("Petar"); salesRep.setLastName("Petrović");
        return salesRep;
    }

    private static AppUserPrincipal ceo() { return principal(1L, "ROLE_CEO"); }

    private static AppUserPrincipal principal(Long id, String role) {
        return new AppUserPrincipal(id, "user@example.com", "hash", "Test", "User", true,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
