package com.ognjen.diplomski.ai;

import static com.ognjen.diplomski.ai.RiskTypes.*;
import static com.ognjen.diplomski.service.CustomerAccessPolicy.*;

import com.ognjen.diplomski.security.AppUserPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** AI-owned persistence only; existing customer visibility comes from the shared policy. */
@Repository
public class RiskStore {
  private final ObjectProvider<JdbcTemplate> provider;
  private final ObjectMapper mapper;

  public RiskStore(ObjectProvider<JdbcTemplate> provider, ObjectMapper mapper) {
    this.provider = provider;
    this.mapper = mapper;
  }

  public JdbcTemplate db() {
    var db = provider.getIfAvailable();
    if (db == null
        || db.queryForObject("select to_regclass('ai_model') is not null", Boolean.class)
            != Boolean.TRUE)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI migration V001 must be applied before using analytics");
    return db;
  }

  public String json(Object value) {
    return mapper.writeValueAsString(value);
  }

  public <T> T read(String value, Class<T> type) {
    return mapper.readValue(value, type);
  }

  private record Scope(String where, List<Object> args) {}

  private Scope scope(AppUserPrincipal user, Long rep, Long manager, String search) {
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
    statuses.forEach(s -> args.add(s.name()));
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

  private static final String FROM =
      " from customer c join app_user u on u.id=c.sales_rep_id left join ai_risk_assessment a on"
          + " a.customer_id=c.id left join ai_model current_model on current_model.active=true ";

  public RiskPage list(
      AppUserPrincipal user,
      Long rep,
      Long manager,
      String search,
      Level risk,
      int page,
      int size) {
    var scope = scope(user, rep, manager, search);
    String where = scope.where();
    var args = new ArrayList<>(scope.args());
    if (risk != null) {
      where += " and coalesce(a.level,'INSUFFICIENT_DATA')=?";
      args.add(risk.name());
    }
    Long count =
        db().queryForObject(
                "select count(*)" + FROM + "where " + where, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    var content =
        db().query(
                "select c.id,c.object_name,c.sales_rep_id,u.manager_id,a.*,current_model.version as"
                    + " active_version"
                    + FROM
                    + "where "
                    + where
                    + " order by case a.level when 'HIGH' then 0 when 'MEDIUM' then 1 when 'LOW'"
                    + " then 2 else 3 end,c.id limit ? offset ?",
                (r, n) -> assessment(r),
                args.toArray());
    return new RiskPage(content, count == null ? 0 : count, page, size);
  }

  public List<Assessment> batch(AppUserPrincipal user, List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    if (ids.size() > 100)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "At most 100 customer IDs are allowed");
    var scope = scope(user, null, null, null);
    var args = new ArrayList<>(scope.args());
    args.addAll(ids);
    return db().query(
            "select c.id,c.object_name,c.sales_rep_id,u.manager_id,a.*,current_model.version as"
                + " active_version"
                + FROM
                + "where "
                + scope.where()
                + " and c.id in ("
                + String.join(",", Collections.nCopies(ids.size(), "?"))
                + ")",
            (r, n) -> assessment(r),
            args.toArray());
  }

  private Assessment assessment(ResultSet r) throws SQLException {
    String level = r.getString("level"), data = r.getString("features");
    Instant at = r.getTimestamp("at") == null ? null : r.getTimestamp("at").toInstant();
    Level risk = level == null ? Level.INSUFFICIENT_DATA : Level.valueOf(level);
    return new Assessment(
        r.getLong("id"),
        r.getString("object_name"),
        r.getLong("sales_rep_id"),
        (Long) r.getObject("manager_id"),
        risk,
        level == null ? "NOT_ASSESSED" : r.getString("status"),
        at,
        (UUID) r.getObject("model_version"),
        data == null ? null : read(data, Features.class),
        r.getString("explanation") == null
            ? List.of()
            : List.of(read(r.getString("explanation"), String[].class)),
        CustomerFeatures.recommendation(risk),
        at == null
            || at.isBefore(Instant.now().minusSeconds(86400 * 2))
            || !Objects.equals(r.getObject("active_version"), r.getObject("model_version")),
        r.getString("previous_level"));
  }

  public Assessment detail(AppUserPrincipal user, long id) {
    var scope = scope(user, null, null, null);
    var args = new ArrayList<>(scope.args());
    args.add(id);
    return db()
        .query(
            "select c.id,c.object_name,c.sales_rep_id,u.manager_id,a.*,current_model.version as"
                + " active_version"
                + FROM
                + "where "
                + scope.where()
                + " and c.id=?",
            (r, n) -> assessment(r),
            args.toArray())
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
  }

  public Summary summary(AppUserPrincipal user, Long rep, Long manager) {
    var scope = scope(user, rep, manager, null);
    var rows =
        db().queryForList(
                "select coalesce(a.level,'INSUFFICIENT_DATA') level,count(*) n,sum(case when"
                    + " a.level='HIGH' then coalesce((a.features::jsonb->>'liters60')::double"
                    + " precision,0) else 0 end) liters,sum(case when (a.level='HIGH' and"
                    + " a.previous_level in ('LOW','MEDIUM')) or (a.level='MEDIUM' and"
                    + " a.previous_level='LOW') then 1 else 0 end) increased"
                    + FROM
                    + "where "
                    + scope.where()
                    + " group by coalesce(a.level,'INSUFFICIENT_DATA')",
                scope.args().toArray());
    Map<String, Long> distribution = new LinkedHashMap<>();
    long total = 0, increased = 0;
    double liters = 0;
    for (var row : rows) {
      long n = ((Number) row.get("n")).longValue();
      distribution.put((String) row.get("level"), n);
      total += n;
      increased += ((Number) row.get("increased")).longValue();
      liters += ((Number) row.get("liters")).doubleValue();
    }
    var teams =
        user.hasRole("CEO")
            ? db().queryForList(
                    "select u.manager_id,max(coalesce(manager.first_name,'') || ' ' ||"
                        + " coalesce(manager.last_name,'')) manager_name,count(*)"
                        + " customers,count(*) filter(where a.level='HIGH') high"
                        + FROM
                        + "left join app_user manager on manager.id=u.manager_id where "
                        + scope.where()
                        + " group by u.manager_id",
                    scope.args().toArray())
            : List.<Map<String, Object>>of();
    long scored = total - distribution.getOrDefault("INSUFFICIENT_DATA", 0L);
    return new Summary(
        total,
        distribution,
        increased,
        scored == 0 ? null : 100.0 * distribution.getOrDefault("HIGH", 0L) / scored,
        liters,
        teams);
  }

  public List<Order> orders(long afterId, long throughId) {
    return db().query(
            "select customer_id,created_at,observed_at,liters from ai_order_observation where"
                + " customer_id>? and customer_id<=?",
            (r, n) ->
                new Order(
                    r.getLong(1),
                    r.getTimestamp(2).toInstant(),
                    r.getTimestamp(3).toInstant(),
                    r.getDouble(4)),
            afterId,
            throughId);
  }

  public List<Map<String, Object>> trends(AppUserPrincipal user, Long rep, Long manager) {
    var scope = scope(user, rep, manager, null);
    return db().queryForList(
            "select assessment_day::text as captured_date,level,model_version,count(*) customers"
                + " from (select distinct on(c.id,h.at::date) c.id,h.at::date as"
                + " assessment_day,h.level,h.model_version from customer c join app_user u on"
                + " u.id=c.sales_rep_id join ai_risk_history h on h.customer_id=c.id where "
                + scope.where()
                + " and h.at>now()-interval '30 days' order by c.id,h.at::date,h.at desc) daily"
                + " group by assessment_day,level,model_version order by assessment_day",
            scope.args().toArray());
  }
}
