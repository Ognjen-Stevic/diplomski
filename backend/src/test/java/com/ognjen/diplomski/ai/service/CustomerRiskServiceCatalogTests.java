package com.ognjen.diplomski.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ognjen.diplomski.ai.domain.RiskTypes.Level;
import com.ognjen.diplomski.ai.domain.RiskTypes.RiskListItem;
import com.ognjen.diplomski.ai.domain.RiskTypes.RiskPage;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import com.ognjen.diplomski.entity.AppUser;
import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import com.ognjen.diplomski.repository.CustomerRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class CustomerRiskServiceCatalogTests {

  private final RiskStore store = mock(RiskStore.class);
  private final CustomerRepository customers = mock(CustomerRepository.class);
  private final CustomerRiskService service = new CustomerRiskService(store, customers);

  @Test
  void returnsAFullPageInSqlOrderWithMatchingMetadata() {
    AppUserPrincipal manager = principal(7L, "MANAGER");
    Customer first = customer(11L, CustomerStatus.APPROVED, 21L, 7L);
    Customer second = customer(12L, CustomerStatus.DRAFT, 22L, 7L);
    when(store.list(manager, null, null, "shop", Level.HIGH, 0, 2))
        .thenReturn(new RiskPage(List.of(risk(12L, Level.HIGH), risk(11L, Level.HIGH)), 4, 0, 2));
    when(customers.findByIdIn(anyCollection())).thenReturn(List.of(first, second));

    var page = service.catalog(manager, "shop", Level.HIGH, 0, 2);

    assertEquals(List.of(12L, 11L), page.content().stream().map(item -> item.id()).toList());
    assertEquals(4, page.totalElements());
    assertEquals(2, page.totalPages());
    assertEquals(2, page.content().size());
  }

  @Test
  void returnsAPartialLastPageWithoutChangingTheSqlTotal() {
    AppUserPrincipal salesRep = principal(21L, "SALES_REP");
    Customer customer = customer(13L, CustomerStatus.APPROVED, 21L, 7L);
    when(store.list(salesRep, null, null, null, Level.LOW, 1, 2))
        .thenReturn(new RiskPage(List.of(risk(13L, Level.LOW)), 3, 1, 2));
    when(customers.findByIdIn(anyCollection())).thenReturn(List.of(customer));

    var page = service.catalog(salesRep, null, Level.LOW, 1, 2);

    assertEquals(1, page.content().size());
    assertEquals(3, page.totalElements());
    assertEquals(2, page.totalPages());
    verify(store).list(salesRep, null, null, null, Level.LOW, 1, 2);
  }

  @Test
  void excludedStatusFailsClosedInsteadOfReturningAShortPage() {
    AppUserPrincipal manager = principal(7L, "MANAGER");
    Customer deleted = customer(14L, CustomerStatus.DELETED, 21L, 7L);
    when(store.list(manager, null, null, null, Level.MEDIUM, 0, 24))
        .thenReturn(new RiskPage(List.of(risk(14L, Level.MEDIUM)), 1, 0, 24));
    when(customers.findByIdIn(anyCollection())).thenReturn(List.of(deleted));

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> service.catalog(manager, null, Level.MEDIUM, 0, 24));

    assertEquals(409, exception.getStatusCode().value());
  }

  @Test
  void managerCannotReceiveAHydratedCustomerFromAnotherTeam() {
    AppUserPrincipal manager = principal(7L, "MANAGER");
    Customer otherTeam = customer(15L, CustomerStatus.APPROVED, 30L, 99L);
    when(store.list(manager, null, null, null, Level.HIGH, 0, 24))
        .thenReturn(new RiskPage(List.of(risk(15L, Level.HIGH)), 1, 0, 24));
    when(customers.findByIdIn(anyCollection())).thenReturn(List.of(otherTeam));

    assertThrows(ResponseStatusException.class,
        () -> service.catalog(manager, null, Level.HIGH, 0, 24));
  }

  @Test
  void salesRepresentativeCannotReceiveAnotherRepresentativesCustomer() {
    AppUserPrincipal salesRep = principal(21L, "SALES_REP");
    Customer otherOwner = customer(16L, CustomerStatus.APPROVED, 22L, 7L);
    when(store.list(salesRep, null, null, null, Level.HIGH, 0, 24))
        .thenReturn(new RiskPage(List.of(risk(16L, Level.HIGH)), 1, 0, 24));
    when(customers.findByIdIn(anyCollection())).thenReturn(List.of(otherOwner));

    assertThrows(ResponseStatusException.class,
        () -> service.catalog(salesRep, null, Level.HIGH, 0, 24));
  }

  @Test
  void missingHydratedEntityFailsTheWholePage() {
    AppUserPrincipal ceo = principal(1L, "CEO");
    when(store.list(ceo, null, null, null, Level.HIGH, 0, 24))
        .thenReturn(new RiskPage(List.of(risk(17L, Level.HIGH)), 1, 0, 24));
    when(customers.findByIdIn(anyCollection())).thenReturn(List.of());

    assertThrows(ResponseStatusException.class,
        () -> service.catalog(ceo, null, Level.HIGH, 0, 24));
  }

  private static RiskListItem risk(long customerId, Level level) {
    return new RiskListItem(customerId, "Customer " + customerId, level, null, "", false);
  }

  private static Customer customer(
      long id, CustomerStatus status, long salesRepId, Long managerId) {
    AppUser manager = null;
    if (managerId != null) {
      manager = new AppUser();
      manager.setId(managerId);
    }
    AppUser salesRep = new AppUser();
    salesRep.setId(salesRepId);
    salesRep.setManager(manager);
    Customer customer = new Customer();
    customer.setId(id);
    customer.setSalesRep(salesRep);
    customer.setStatus(status);
    customer.setObjectName("Object " + id);
    customer.setCompanyName("Company " + id);
    customer.setPib("123456789");
    customer.setAddress("Address " + id);
    return customer;
  }

  private static AppUserPrincipal principal(long id, String role) {
    return new AppUserPrincipal(
        id,
        "user" + id + "@example.test",
        "hash",
        "Test",
        "User",
        true,
        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }
}
