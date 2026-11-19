package com.ognjen.diplomski.ai.persistence;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;

import com.ognjen.diplomski.security.AppUserPrincipal;
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
  private final RiskScopeFactory scopes = new RiskScopeFactory();
  private final RiskAssessmentRowMapper assessments;
  private volatile boolean schemaValidated;

  public RiskStore(ObjectProvider<JdbcTemplate> provider, ObjectMapper mapper) {
    this.provider = provider;
    this.mapper = mapper;
    this.assessments = new RiskAssessmentRowMapper(mapper);
  }

  public JdbcTemplate db() {
    var db = provider.getIfAvailable();
    if (db == null)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI migration V001 must be applied before using analytics");
    if (!schemaValidated) validateSchema(db);
    return db;
  }

  private synchronized void validateSchema(JdbcTemplate db) {
    if (schemaValidated) return;
    if (db.queryForObject("select to_regclass('ai_model') is not null", Boolean.class)
        != Boolean.TRUE)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI migration V001 must be applied before using analytics");
    schemaValidated = true;
  }

  public String json(Object value) {
    return mapper.writeValueAsString(value);
  }

  public <T> T read(String value, Class<T> type) {
    return mapper.readValue(value, type);
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
    var scope = scopes.create(user, rep, manager, search);
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
                "select c.id,c.object_name,a.level,a.at,a.model_version,current_model.version as"
                    + " active_version"
                    + FROM
                    + "where "
                    + where
                    + " order by case a.level when 'HIGH' then 0 when 'MEDIUM' then 1 when 'LOW'"
                    + " then 2 else 3 end,c.id limit ? offset ?",
                (r, n) -> assessments.list(r),
                args.toArray());
    return new RiskPage(content, count == null ? 0 : count, page, size);
  }

  public List<RiskBatchItem> batch(AppUserPrincipal user, List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    if (ids.size() > 100)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "At most 100 customer IDs are allowed");
    var scope = scopes.create(user, null, null, null);
    var args = new ArrayList<>(scope.args());
    args.addAll(ids);
    return db().query(
            "select c.id,a.level"
                + FROM
                + "where "
                + scope.where()
                + " and c.id in ("
                + String.join(",", Collections.nCopies(ids.size(), "?"))
                + ")",
            (r, n) -> assessments.batch(r),
            args.toArray());
  }

  public Assessment detail(AppUserPrincipal user, long id) {
    var scope = scopes.create(user, null, null, null);
    var args = new ArrayList<>(scope.args());
    args.add(id);
    return db()
        .query(
            "select a.level,a.at,a.status,a.model_version,a.features,a.explanation,current_model.version as"
                + " active_version"
                + FROM
                + "where "
                + scope.where()
                + " and c.id=?",
            (r, n) -> assessments.detail(r),
            args.toArray())
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
  }

  public Summary summary(AppUserPrincipal user, Long rep, Long manager) {
    var scope = scopes.create(user, rep, manager, null);
    var rows =
        db().queryForList(
                "select coalesce(a.level,'INSUFFICIENT_DATA') level,count(*) n,sum(case when"
                    + " (a.level='HIGH' and"
                    + " a.previous_level in ('LOW','MEDIUM')) or (a.level='MEDIUM' and"
                    + " a.previous_level='LOW') then 1 else 0 end) increased"
                    + FROM
                    + "where "
                    + scope.where()
                    + " group by coalesce(a.level,'INSUFFICIENT_DATA')",
                scope.args().toArray());
    Map<String, Long> distribution = new LinkedHashMap<>();
    long total = 0, increased = 0;
    for (var row : rows) {
      long n = ((Number) row.get("n")).longValue();
      distribution.put((String) row.get("level"), n);
      total += n;
      increased += ((Number) row.get("increased")).longValue();
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

  public List<Order> orders(Collection<Long> customerIds) {
    if (customerIds.isEmpty()) return List.of();
    String placeholders = String.join(",", Collections.nCopies(customerIds.size(), "?"));
    return db().query(
        "select customer_id,created_at,observed_at,liters from ai_order_observation where"
            + " customer_id in ("
            + placeholders
            + ") order by customer_id,created_at",
        (r, n) ->
            new Order(
                r.getLong(1),
                r.getTimestamp(2).toInstant(),
                r.getTimestamp(3).toInstant(),
                r.getDouble(4)),
        customerIds.toArray());
  }

}
