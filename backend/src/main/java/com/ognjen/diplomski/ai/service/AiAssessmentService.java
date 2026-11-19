package com.ognjen.diplomski.ai.service;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;

import com.ognjen.diplomski.ai.audit.AiAuditLogger;
import com.ognjen.diplomski.ai.domain.CustomerFeatures;
import com.ognjen.diplomski.ai.lifecycle.model.AiModelService;
import com.ognjen.diplomski.ai.model.DecisionTreeModel;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Recalculates persisted predictions; it never trains or promotes a model. */
@Service
public class AiAssessmentService {
  public record BatchResult(long attempted, long succeeded, long failed) {}

  private record Work(
      long customerId, long generation, int attempts, boolean snapshot, String source) {}

  private record AssessmentInput(
      long customerId, Instant customerSince, String previousLevel, List<Order> orders) {}

  private record PreparedBatch(
      Map<Long, AssessmentInput> inputs, AiModelService.Availability availability) {}

  private final RiskStore store;
  private final AiModelService models;
  private final ObjectProvider<PlatformTransactionManager> transactionManagers;
  private final AiOperationalStatusService health;
  private final AiAuditLogger audit;
  private final int maxRetries;
  private final ThreadLocal<PreparedBatch> preparedBatch = new ThreadLocal<>();

  public AiAssessmentService(
      RiskStore store,
      AiModelService models,
      ObjectProvider<PlatformTransactionManager> transactionManagers,
      AiOperationalStatusService health,
      AiAuditLogger audit,
      @Value("${ai.lifecycle.max-retries:3}") int maxRetries) {
    this.store = store;
    this.models = models;
    this.transactionManagers = transactionManagers;
    this.health = health;
    this.audit = audit;
    this.maxRetries = maxRetries;
  }

  public void refreshCustomer(long customerId, Instant at, boolean snapshot) throws Exception {
    refreshCustomer(customerId, at, snapshot, "DIRECT_REFRESH");
  }

  public void refreshCustomer(long customerId, Instant at, boolean snapshot, String trigger)
      throws Exception {
    PreparedBatch prepared = preparedBatch.get();
    AssessmentInput input = prepared == null
        ? loadInputs(List.of(customerId)).get(customerId)
        : prepared.inputs().get(customerId);
    if (input == null) return;
    refreshCustomer(
        input,
        prepared == null ? models.availability() : prepared.availability(),
        at,
        snapshot,
        trigger);
  }

  private void refreshCustomer(
      AssessmentInput input,
      AiModelService.Availability availability,
      Instant at,
      boolean snapshot,
      String trigger)
      throws Exception {
    long started = System.nanoTime();
    long customerId = input.customerId();
    var active = availability.active();
    Features features = CustomerFeatures.extract(at, input.customerSince(), input.orders());
    DecisionTreeModel.PredictionTrace trace =
        features.sufficient() && active != null
            ? DecisionTreeModel.trace(active.tree(), features)
            : null;
    Level level = trace == null ? Level.INSUFFICIENT_DATA : Level.values()[trace.prediction()];
    String assessmentStatus =
        !features.sufficient()
            ? "INSUFFICIENT_DATA"
            : active == null ? "MODEL_UNAVAILABLE" : "ASSESSED";
    List<String> explanation =
        active != null && "ASSESSED".equals(assessmentStatus)
            ? DecisionTreeModel.path(active.tree(), features)
            : List.of();
    UUID version = active == null ? null : active.version();
    new TransactionTemplate(transactionManagers.getObject()).executeWithoutResult(
        tx -> {
          if (snapshot)
            store
                .db()
                .update(
                    "insert into ai_risk_snapshot(customer_id,at,features) select ?,?,? where not"
                        + " exists(select 1 from ai_risk_snapshot where customer_id=? and"
                        + " at::date=?::timestamptz::date)",
                    customerId,
                    Timestamp.from(at),
                    store.json(features),
                    customerId,
                    Timestamp.from(at));
          store
              .db()
              .update(
                  """
                  insert into ai_risk_assessment(customer_id,at,level,status,model_version,features,explanation)
                  values (?,?,?,?,?,?,?) on conflict(customer_id) do update set
                  previous_level=case when ai_risk_assessment.model_version=excluded.model_version then ai_risk_assessment.level else null end,
                  at=excluded.at,level=excluded.level,status=excluded.status,model_version=excluded.model_version,
                  features=excluded.features,explanation=excluded.explanation
                  """,
                  customerId,
                  Timestamp.from(at),
                  level.name(),
                  assessmentStatus,
                  version,
                  store.json(features),
                  store.json(explanation));
          store
              .db()
              .update(
                  "insert into ai_risk_history(customer_id,at,level,model_version) values (?,?,?,?)"
                      + " on conflict(customer_id,at) do nothing",
                  customerId,
                  Timestamp.from(at),
                  level.name(),
                  version);
        });
    var details = new LinkedHashMap<String, Object>();
    details.put("assessmentAt", at);
    details.put("trigger", trigger);
    details.put("features", features);
    details.put("previousRisk", input.previousLevel());
    details.put("newRisk", level.name());
    details.put("traversal", trace == null ? List.of() : trace.traversal());
    details.put("prediction", level.name());
    audit.decision(
        "CUSTOMER_RISK_ASSESSED",
        AiAuditLogger.Severity.INFO,
        new AiAuditLogger.Context(
            UUID.randomUUID().toString(),
            null,
            version,
            active == null ? null : active.modelVersion(),
            customerId,
            assessmentStatus,
            (System.nanoTime() - started) / 1_000_000),
        details);
  }

  public BatchResult refreshAll(Instant at, boolean snapshots, String source) {
    long attempted = 0, succeeded = 0, failed = 0, after = 0;
    while (true) {
      var ids =
          store
              .db()
              .queryForList("select id from customer where id>? order by id limit 500", after);
      if (ids.isEmpty()) break;
      List<Long> customerIds = ids.stream()
          .map(row -> ((Number) row.get("id")).longValue())
          .toList();
      Map<Long, AssessmentInput> inputs = loadInputs(customerIds);
      AiModelService.Availability availability = models.availability();
      preparedBatch.set(new PreparedBatch(inputs, availability));
      try {
        for (var row : ids) {
          long id = ((Number) row.get("id")).longValue();
          attempted++;
          try {
            refreshCustomer(id, at, snapshots, source);
            resolveRecoveredWork(id);
            succeeded++;
          } catch (Exception error) {
            failed++;
            enqueue(id, null, source, snapshots);
            health.fail(
                AiOperationalStatusService.Area.ASSESSMENT,
                "ASSESSMENT_REFRESH_FAILED: " + safeMessage(error));
          }
          after = id;
        }
      } finally {
        preparedBatch.remove();
      }
    }
    store
        .db()
        .update(
            "update ai_system_state set last_assessment_refresh_at=?,"
                + (snapshots ? "last_snapshot_at=?," : "")
                + "updated_at=now() where singleton",
            snapshots
                ? new Object[] {Timestamp.from(at), Timestamp.from(at)}
                : new Object[] {Timestamp.from(at)});
    if (failed == 0) health.recover(AiOperationalStatusService.Area.ASSESSMENT);
    return new BatchResult(attempted, succeeded, failed);
  }

  /** Runs the daily point-in-time maintenance as a durable, observable job. */
  public void maintainDailySnapshots(Instant at) {
    long started = System.nanoTime();
    UUID jobId = UUID.randomUUID();
    store
        .db()
        .update(
            "insert into ai_job_execution(id,job_type,status,owner_id) values (?,?,'RUNNING',?)",
            jobId,
            "SNAPSHOT",
            jobId);
    try {
      BatchResult result = refreshAll(at, true, "DAILY_SNAPSHOT");
      store
          .db()
          .update(
              "update ai_job_execution set status=?,completed_at=now(),outcome=? where id=?",
              result.failed() == 0 ? "COMPLETED" : "FAILED",
              "Dnevne procene: " + result.succeeded() + " uspešno, " + result.failed() + " za retry.",
              jobId);
      if (result.failed() == 0) health.recover(AiOperationalStatusService.Area.SNAPSHOT);
      else
        health.fail(
            AiOperationalStatusService.Area.SNAPSHOT,
            "DAILY_ASSESSMENT_FAILED: " + result.failed() + " customer assessments queued");
      audit.lifecycle(
          "DAILY_REASSESSMENT_COMPLETED",
          result.failed() == 0 ? AiAuditLogger.Severity.INFO : AiAuditLogger.Severity.WARN,
          new AiAuditLogger.Context(
              jobId.toString(), jobId, null, null, null,
              result.failed() == 0 ? "COMPLETED" : "FAILED",
              (System.nanoTime() - started) / 1_000_000),
          Map.of(
              "attempted", result.attempted(),
              "succeeded", result.succeeded(),
              "failed", result.failed()));
    } catch (Exception error) {
      String message = safeMessage(error);
      store
          .db()
          .update(
              "update ai_job_execution set status='FAILED',completed_at=now(),outcome=?,error_message=? where id=?",
              "Dnevno osvežavanje nije uspelo.",
              message,
              jobId);
      health.fail(AiOperationalStatusService.Area.SNAPSHOT, message);
      audit.error(
          "DAILY_REASSESSMENT_FAILED",
          new AiAuditLogger.Context(
              jobId.toString(), jobId, null, null, null, "FAILED",
              (System.nanoTime() - started) / 1_000_000),
          "SNAPSHOT",
          error,
          Map.of());
      throw new IllegalStateException("Daily AI snapshot maintenance failed", error);
    }
  }

  private static String safeMessage(Exception error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }

  public BatchResult refreshStale(Instant at) {
    var availability = models.availability();
    String condition =
        availability.usable()
            ? "a.at is null or a.at<now()-interval '2 days' or a.status='MODEL_UNAVAILABLE'"
                + " or (a.status='ASSESSED' and a.model_version is distinct from ?)"
            : "a.at is null or a.status='ASSESSED' or a.model_version is not null";
    var ids =
        availability.usable()
            ? store
                .db()
                .queryForList(
                    "select c.id from customer c left join ai_risk_assessment a on"
                        + " a.customer_id=c.id where "
                        + condition
                        + " order by c.id limit 500",
                    availability.active().version())
            : store
                .db()
                .queryForList(
                    "select c.id from customer c left join ai_risk_assessment a on"
                        + " a.customer_id=c.id where "
                        + condition
                        + " order by c.id limit 500");
    long succeeded = 0, failed = 0;
    List<Long> customerIds = ids.stream()
        .map(row -> ((Number) row.get("id")).longValue())
        .toList();
    Map<Long, AssessmentInput> inputs = loadInputs(customerIds);
    preparedBatch.set(new PreparedBatch(inputs, availability));
    try {
      for (var row : ids) {
        long customerId = ((Number) row.get("id")).longValue();
        try {
          refreshCustomer(customerId, at, false, "STALE_REFRESH");
          resolveRecoveredWork(customerId);
          succeeded++;
        } catch (Exception error) {
          failed++;
          enqueue(customerId, null, "STALE_REFRESH", false);
          health.fail(
              AiOperationalStatusService.Area.ASSESSMENT,
              "ASSESSMENT_REFRESH_FAILED: " + safeMessage(error));
        }
      }
    } finally {
      preparedBatch.remove();
    }
    if (!ids.isEmpty())
      store
          .db()
          .update(
              "update ai_system_state set last_assessment_refresh_at=?,updated_at=now() where singleton",
              Timestamp.from(at));
    if (failed == 0) health.recover(AiOperationalStatusService.Area.ASSESSMENT);
    return new BatchResult(ids.size(), succeeded, failed);
  }

  public void enqueue(long customerId, Long requisitionId, String source, boolean snapshot) {
    store
        .db()
        .update(
            """
            insert into ai_assessment_work(customer_id,requisition_id,source,snapshot)
            values (?,?,?,?) on conflict(customer_id) do update set
            requisition_id=coalesce(excluded.requisition_id,ai_assessment_work.requisition_id),
            source=excluded.source,snapshot=ai_assessment_work.snapshot or excluded.snapshot,
            generation=ai_assessment_work.generation+1,
            status=case when ai_assessment_work.status='PROCESSING' and
              ai_assessment_work.locked_until>now() then 'PROCESSING' else 'PENDING' end,
            attempts=case when ai_assessment_work.status='PROCESSING' and
              ai_assessment_work.locked_until>now() then ai_assessment_work.attempts else 0 end,
            available_at=now(),completed_at=null,last_error=null,updated_at=now()
            """,
            customerId,
            requisitionId,
            source,
            snapshot);
  }

  public BatchResult processPending(Instant at, int limit) {
    List<Work> work = claim(limit);
    if (work.isEmpty()) return new BatchResult(0, 0, 0);
    Map<Long, AssessmentInput> inputs = loadInputs(
        work.stream().map(Work::customerId).distinct().toList());
    AiModelService.Availability availability = models.availability();
    long succeeded = 0, failed = 0;
    preparedBatch.set(new PreparedBatch(inputs, availability));
    try {
      for (Work item : work) {
        try {
          refreshCustomer(item.customerId(), at, item.snapshot(), item.source());
        int completed =
            store
                .db()
                .update(
                    "update ai_assessment_work set status='COMPLETED',locked_until=null,"
                        + "completed_at=now(),last_error=null,updated_at=now()"
                        + " where customer_id=? and generation=? and status='PROCESSING'",
                    item.customerId(),
                    item.generation());
        if (completed == 0)
          store
              .db()
              .update(
                  "update ai_assessment_work set status='PENDING',attempts=0,available_at=now(),"
                      + "locked_until=null,updated_at=now() where customer_id=? and status='PROCESSING'",
                  item.customerId());
        if (item.attempts() > 0)
          audit.lifecycle(
              "ASSESSMENT_RETRY_RECOVERED",
              AiAuditLogger.Severity.INFO,
              new AiAuditLogger.Context(
                  Long.toString(item.customerId()), null, null, null, item.customerId(),
                  "COMPLETED", null),
              Map.of("attempt", item.attempts() + 1, "trigger", item.source()));
          succeeded++;
        } catch (Exception error) {
          failed++;
        int attempts = item.attempts() + 1;
        boolean terminal = attempts >= maxRetries;
        int backoffMinutes = Math.min(60, 5 * attempts);
        int recorded =
            store
            .db()
            .update(
                "update ai_assessment_work set status=?,available_at=now()+make_interval(mins=>?),"
                    + "locked_until=null,last_error=?,updated_at=now()"
                    + " where customer_id=? and generation=?",
                terminal ? "FAILED" : "PENDING",
                backoffMinutes,
                safeMessage(error),
                item.customerId(),
                item.generation());
        if (recorded == 0)
          store
              .db()
              .update(
                  "update ai_assessment_work set status='PENDING',attempts=0,available_at=now(),"
                      + "locked_until=null,updated_at=now() where customer_id=? and status='PROCESSING'",
                  item.customerId());
        health.fail(
            AiOperationalStatusService.Area.ASSESSMENT,
            "ASSESSMENT_REFRESH_FAILED: " + safeMessage(error));
          audit.error(
            terminal ? "ASSESSMENT_RETRY_EXHAUSTED" : "ASSESSMENT_RETRY_SCHEDULED",
            new AiAuditLogger.Context(
                Long.toString(item.customerId()), null, null, null, item.customerId(),
                terminal ? "FAILED" : "PENDING", null),
            "ASSESSMENT",
            error,
            Map.of(
                "attempt", attempts,
                "maxAttempts", maxRetries,
                "backoffMinutes", backoffMinutes,
                "trigger", item.source()));
        }
      }
    } finally {
      preparedBatch.remove();
    }
    if (failed == 0 && succeeded > 0)
      health.recover(AiOperationalStatusService.Area.ASSESSMENT);
    return new BatchResult(work.size(), succeeded, failed);
  }

  private List<Work> claim(int limit) {
    return new TransactionTemplate(transactionManagers.getObject())
        .execute(
            tx -> {
              var rows =
                  store
                      .db()
                      .queryForList(
                          "select customer_id,generation,attempts,snapshot,source from ai_assessment_work"
                              + " where (status='PENDING' and available_at<=now()) or"
                              + " (status='PROCESSING' and locked_until<now()) order by available_at,customer_id"
                              + " for update skip locked limit ?",
                          limit);
              var result = new ArrayList<Work>();
              for (var row : rows) {
                long customerId = ((Number) row.get("customer_id")).longValue();
                long generation = ((Number) row.get("generation")).longValue();
                int attempts = ((Number) row.get("attempts")).intValue();
                store
                    .db()
                    .update(
                        "update ai_assessment_work set status='PROCESSING',attempts=attempts+1,"
                            + "locked_until=now()+interval '10 minutes',updated_at=now()"
                            + " where customer_id=? and generation=?",
                        customerId,
                        generation);
                result.add(
                    new Work(
                        customerId,
                        generation,
                        attempts,
                        Boolean.TRUE.equals(row.get("snapshot")),
                        Objects.toString(row.get("source"), "ASSESSMENT_RETRY")));
              }
              return result;
            });
  }

  private void resolveRecoveredWork(long customerId) {
    store
        .db()
        .update(
            "update ai_assessment_work set status='COMPLETED',completed_at=now(),last_error=null,"
                + "locked_until=null,updated_at=now() where customer_id=? and"
                + " status in ('FAILED','PENDING') and last_error is not null",
            customerId);
  }

  private Map<Long, AssessmentInput> loadInputs(List<Long> customerIds) {
    if (customerIds.isEmpty()) return Map.of();
    String placeholders = String.join(",", Collections.nCopies(customerIds.size(), "?"));
    var rows = store.db().queryForList(
        "select c.id,c.created_at,a.level previous_level from customer c"
            + " left join ai_risk_assessment a on a.customer_id=c.id where c.id in ("
            + placeholders
            + ")",
        customerIds.toArray());
    Map<Long, List<Order>> ordersByCustomer = new HashMap<>();
    for (Order order : store.orders(customerIds)) {
      ordersByCustomer.computeIfAbsent(order.customerId(), ignored -> new ArrayList<>()).add(order);
    }
    Map<Long, AssessmentInput> result = new HashMap<>();
    for (var row : rows) {
      long customerId = ((Number) row.get("id")).longValue();
      result.put(
          customerId,
          new AssessmentInput(
              customerId,
              ((Timestamp) row.get("created_at")).toInstant(),
              Objects.toString(row.get("previous_level"), null),
              ordersByCustomer.getOrDefault(customerId, List.of())));
    }
    return result;
  }
}
