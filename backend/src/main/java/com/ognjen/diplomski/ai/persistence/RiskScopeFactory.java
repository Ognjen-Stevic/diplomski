package com.ognjen.diplomski.ai.persistence;

import static com.ognjen.diplomski.security.CustomerAccessPolicy.*;

import com.ognjen.diplomski.security.AppUserPrincipal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Converts the shared customer-access policy and UI filters into a parameterized SQL scope. */
final class RiskScopeFactory {
  record Scope(String where, List<Object> args) {}

  Scope create(AppUserPrincipal user, Long rep, Long manager, String search) {
    if (manager != null && !user.hasRole("CEO"))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CEO can select a manager");
    if (rep != null && user.hasRole("SALES_REP") && !rep.equals(user.id()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only own customers are accessible");
    var visibility = customerAccessScope(user);
    var args = new ArrayList<Object>();
    var statuses = visibleStatuses(user);
    if (statuses.isEmpty())
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Customer analytics is not permitted");
    String statusSlots = String.join(",", Collections.nCopies(statuses.size(), "?"));
    statuses.forEach(status -> args.add(status.name()));
    String where = " c.status in (" + statusSlots + ") and (";
    if (visibility.allCustomers()) where += "true";
    else if (visibility.managedSalesReps()) {
      where += "u.manager_id=?";
      args.add(user.id());
    } else {
      where += "c.sales_rep_id=?";
      args.add(user.id());
    }
    where += ")";
    if (rep != null) {
      where += " and c.sales_rep_id=?";
      args.add(rep);
    }
    if (manager != null) {
      where += " and u.manager_id=?";
      args.add(manager);
    }
    if (search != null && !search.isBlank()) {
      where +=
          " and (lower(c.object_name) like ? or lower(c.company_name) like ? or lower(c.pib) like ?"
              + " or lower(coalesce(c.phone,'')) like ? or lower(c.address) like ?)";
      for (int i = 0; i < 5; i++) args.add("%" + search.trim().toLowerCase(Locale.ROOT) + "%");
    }
    return new Scope(where, args);
  }
}
