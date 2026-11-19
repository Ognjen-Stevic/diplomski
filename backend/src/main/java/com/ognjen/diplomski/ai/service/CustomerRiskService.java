package com.ognjen.diplomski.ai.service;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;

import com.ognjen.diplomski.ai.persistence.RiskStore;
import com.ognjen.diplomski.security.AppUserPrincipal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CustomerRiskService {
  private final RiskStore store;
  private final com.ognjen.diplomski.repository.CustomerRepository customers;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public com.ognjen.diplomski.dto.CustomerPageResponse catalog(
      AppUserPrincipal user, String search, Level risk, int page, int size) {
    var rows = store.list(user, null, null, search, risk, page, size);
    var byId = new HashMap<Long, com.ognjen.diplomski.entity.Customer>();
    if (!rows.content().isEmpty())
      customers
          .findByIdIn(rows.content().stream().map(RiskListItem::customerId).toList())
          .forEach(c -> byId.put(c.getId(), c));
    var content =
        rows.content().stream()
            .map(a -> requireConsistentCatalogCustomer(user, a.customerId(), byId))
            .map(c -> com.ognjen.diplomski.dto.CustomerResponse.from(c, user))
            .toList();
    return new com.ognjen.diplomski.dto.CustomerPageResponse(
        content, page, size, rows.total(), (int) Math.ceil((double) rows.total() / size));
  }

  private static com.ognjen.diplomski.entity.Customer requireConsistentCatalogCustomer(
      AppUserPrincipal user,
      long customerId,
      Map<Long, com.ognjen.diplomski.entity.Customer> customersById) {
    var customer = customersById.get(customerId);
    if (customer == null
        || !com.ognjen.diplomski.security.CustomerAccessPolicy.canAccessCustomer(user, customer)
        || !com.ognjen.diplomski.security.CustomerAccessPolicy.visibleStatuses(user)
            .contains(customer.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Customer catalog changed; retry the request");
    }
    return customer;
  }

  public RiskPage list(
      AppUserPrincipal u, Long r, Long m, String search, Level risk, int page, int size) {
    return store.list(u, r, m, search, risk, page, size);
  }

  public Assessment detail(AppUserPrincipal u, long id) {
    return store.detail(u, id);
  }

  public Summary summary(AppUserPrincipal u, Long r, Long m) {
    return store.summary(u, r, m);
  }

  public List<RiskBatchItem> batch(AppUserPrincipal u, List<Long> ids) {
    return store.batch(u, ids);
  }

  public List<ActivityItem> activities(AppUserPrincipal user, long id) {
    store.detail(user, id);
    return store
        .db()
        .query(
            "select id,type,at,outcome from ai_risk_activity where"
                + " customer_id=? order by at desc limit 100",
            (row, index) ->
                new ActivityItem(
                    row.getLong("id"),
                    row.getString("type"),
                    row.getTimestamp("at").toInstant(),
                    row.getString("outcome")),
            id);
  }

  @Transactional
  public void activity(AppUserPrincipal user, long id, ActivityRequest request) {
    var assessment = store.detail(user, id);
    if (!user.hasRole("SALES_REP"))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only owning representatives record activities");
    if (assessment.at() == null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Capture an assessment before recording an activity");
    if (!Set.of("CONTACT", "INVESTIGATE", "MONITOR").contains(request.type()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown activity type");
    store
        .db()
        .update(
            "insert into ai_risk_activity(customer_id,actor_id,assessment_at,type,notes,outcome)"
                + " values (?,?,?,?,?,?)",
            id,
            user.id(),
            java.sql.Timestamp.from(assessment.at()),
            request.type(),
            request.notes(),
            request.outcome());
  }
}
