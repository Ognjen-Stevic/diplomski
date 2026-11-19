package com.ognjen.diplomski.ai.lifecycle.capture;

import static com.ognjen.diplomski.ai.support.AiErrorMessages.safe;

import com.ognjen.diplomski.ai.audit.AiAuditLogger;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import com.ognjen.diplomski.ai.service.AiAssessmentService;
import com.ognjen.diplomski.ai.service.AiOperationalStatusService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Idempotent outbox processing and periodic observation reconciliation. */
@Service
public class AiCaptureService {
  private final RiskStore store;
  private final AiAssessmentService assessments;
  private final ObjectProvider<PlatformTransactionManager> transactionManagers;
  private final AiOperationalStatusService health;
  private final AiAuditLogger audit;
  private final int maxRetries;

  public AiCaptureService(
      RiskStore store,
      AiAssessmentService assessments,
      ObjectProvider<PlatformTransactionManager> transactionManagers,
      AiOperationalStatusService health,
      AiAuditLogger audit,
      @Value("${ai.lifecycle.max-retries:3}") int maxRetries) {
    this.store = store;
    this.assessments = assessments;
    this.transactionManagers = transactionManagers;
    this.health = health;
    this.audit = audit;
    this.maxRetries = maxRetries;
  }

  public int reconcileAndProcess() {
    long started = System.nanoTime();
    UUID job = startJob("CAPTURE");
    try {
      recoverExpiredEvents();
      store
          .db()
          .update(
              """
              insert into ai_outbox_event(event_type,requisition_id)
              select 'REQUISITION_COMPLETED',r.id from requisition r
              left join ai_order_observation o on o.requisition_id=r.id
              where r.status='COMPLETED' and r.type='NORMAL' and o.requisition_id is null
              on conflict(requisition_id) do nothing
              """);
      int processed = processBatch(100);
      var failedEvents =
          store
              .db()
              .queryForList(
                  "select last_error from ai_outbox_event where status='FAILED' order by id desc limit 1");
      String persistentError =
          failedEvents.isEmpty() ? null : Objects.toString(failedEvents.getFirst().get("last_error"), null);
      Instant now = Instant.now();
      store
          .db()
          .update(
              "insert into ai_capture_run(at) values (?) on conflict do nothing",
              Timestamp.from(now));
      String assessmentWarning = null;
      try {
        var queued = assessments.processPending(now, 100);
        var stale = assessments.refreshStale(now);
        long assessmentFailures = queued.failed() + stale.failed();
        if (assessmentFailures > 0)
          assessmentWarning =
              "ASSESSMENT_REFRESH_FAILED: " + assessmentFailures + " assessment attempts failed";
      } catch (Exception error) {
        assessmentWarning = "ASSESSMENT_REFRESH_FAILED: " + safe(error);
        recordAssessmentFailure(assessmentWarning, error);
      }
      String monitoringError = persistentError != null ? persistentError : assessmentWarning;
      if (persistentError == null) health.recover(AiOperationalStatusService.Area.CAPTURE);
      else health.fail(AiOperationalStatusService.Area.CAPTURE, persistentError);
      if (monitoringError == null)
        store
            .db()
            .update(
                "update ai_system_state set last_capture_at=?,operational_status=case when"
                    + " persistent_error is null then 'RUNNING' else 'DEGRADED' end,updated_at=now()"
                    + " where singleton",
                Timestamp.from(now));
      else
        store
            .db()
            .update(
                "update ai_system_state set last_capture_at=?,operational_status='DEGRADED',"
                    + "persistent_error=?,updated_at=now() where singleton",
                Timestamp.from(now),
                monitoringError);
      finishJob(
          job,
          monitoringError == null ? "COMPLETED" : "FAILED",
          "Obrađeno događaja: " + processed,
          monitoringError);
      if (processed > 0 || monitoringError != null)
        audit.lifecycle(
            "CAPTURE_BATCH_COMPLETED",
            monitoringError == null ? AiAuditLogger.Severity.INFO : AiAuditLogger.Severity.WARN,
            new AiAuditLogger.Context(
                job.toString(), job, null, null, null,
                monitoringError == null ? "COMPLETED" : "FAILED",
                (System.nanoTime() - started) / 1_000_000),
            Map.of("processedEvents", processed, "assessmentStatus", monitoringError == null ? "OK" : "FAILED"));
      return processed;
    } catch (Exception error) {
      fail(job, "Capture/reconciliation failed", error);
      return 0;
    }
  }

  int processBatch(int limit) {
    var events =
        new TransactionTemplate(transactionManagers.getObject()).execute(
            status -> {
              var rows =
                  store
                      .db()
                      .queryForList(
                          "select id,requisition_id,created_at,attempts from ai_outbox_event"
                              + " where status='PENDING' and available_at<=now() order by id"
                              + " for update skip locked limit ?",
                          limit);
              for (var row : rows)
                store
                    .db()
                    .update(
                        "update ai_outbox_event set status='PROCESSING',locked_until=now()+interval"
                            + " '10 minutes',attempts=attempts+1 where id=?",
                        row.get("id"));
              return rows;
            });
    int processed = 0;
    for (var event : Objects.requireNonNullElse(events, List.<Map<String, Object>>of())) {
      long id = ((Number) event.get("id")).longValue();
      long requisition = ((Number) event.get("requisition_id")).longValue();
      Instant observed = ((Timestamp) event.get("created_at")).toInstant();
      Long customer = null;
      try {
        store
            .db()
            .update(
                    """
                    insert into ai_order_observation(requisition_id,customer_id,created_at,observed_at,liters)
                    select r.id,r.customer_id,r.created_at,?,sum(i.quantity*p.volume_liters)::double precision
                    from requisition r join requisition_item i on i.requisition_id=r.id
                    join product p on p.id=i.product_id
                    where r.id=? and r.status='COMPLETED' and r.type='NORMAL'
                    group by r.id,r.customer_id,r.created_at on conflict(requisition_id) do nothing
                    """,
                    Timestamp.from(observed),
                    requisition);
        customer =
            store
                .db()
                .query(
                    "select customer_id from ai_order_observation where requisition_id=?",
                    r -> r.next() ? r.getLong(1) : null,
                    requisition);
        if (customer == null)
          throw new IllegalStateException("Captured observation has no customer");
        assessments.enqueue(customer, requisition, "REQUISITION_COMPLETED", false);
        store
            .db()
            .update(
                "update ai_outbox_event set status='COMPLETED',completed_at=now(),locked_until=null,"
                    + "last_error=null where id=?",
                id);
        processed++;
      } catch (Exception error) {
        int attempts = ((Number) event.get("attempts")).intValue() + 1;
        String status = attempts >= maxRetries ? "FAILED" : "PENDING";
        store
            .db()
            .update(
                "update ai_outbox_event set status=?,available_at=now()+interval '5 minutes',"
                    + "locked_until=null,last_error=? where id=?",
                status,
                safe(error),
                id);
        LoggerFactory.getLogger(AiCaptureService.class)
            .error("AI capture event {} failed", id, error);
        audit.error(
            "CAPTURE_EVENT_FAILED",
            new AiAuditLogger.Context(
                Long.toString(id), null, null, null, customer, status, null),
            "CAPTURE",
            error,
            Map.of("requisitionId", requisition, "attempt", attempts));
        continue;
      }
    }
    return processed;
  }

  public void recoverExpiredEvents() {
    store
        .db()
        .update(
            "update ai_outbox_event set status='PENDING',locked_until=null where"
                + " status='PROCESSING' and locked_until<now()");
    store
        .db()
        .update(
            "update ai_outbox_event set status='PENDING',attempts=0,available_at=now() where"
                + " status='FAILED' and available_at<now()-interval '55 minutes'");
  }

  private UUID startJob(String type) {
    UUID id = UUID.randomUUID();
    store
        .db()
        .update(
            "insert into ai_job_execution(id,job_type,status,owner_id) values (?,?,'RUNNING',?)",
            id,
            type,
            id);
    return id;
  }

  private void finishJob(UUID id, String status, String outcome, String error) {
    store
        .db()
        .update(
            "update ai_job_execution set status=?,completed_at=now(),outcome=?,error_message=?"
                + " where id=?",
            status,
            outcome,
            error,
            id);
  }

  private void fail(UUID job, String message, Exception error) {
    String safe = safe(error);
    finishJob(job, "FAILED", message, safe);
    try {
      health.fail(AiOperationalStatusService.Area.CAPTURE, safe);
    } catch (DataAccessException | org.springframework.web.server.ResponseStatusException ignored) {
      // The original failure is already logged below.
    }
    audit.error(
        "CAPTURE_JOB_FAILED",
        new AiAuditLogger.Context(job.toString(), job, null, null, null, "FAILED", null),
        "CAPTURE",
        error,
        Map.of());
    LoggerFactory.getLogger(AiCaptureService.class).error(message, error);
  }

  private void recordAssessmentFailure(String message, Exception error) {
    try {
      health.fail(AiOperationalStatusService.Area.ASSESSMENT, message);
    } catch (DataAccessException ignored) {
      // Preserve the original assessment failure in the log.
    }
    LoggerFactory.getLogger(AiCaptureService.class)
        .warn("Order capture succeeded but customer assessment refresh failed", error);
  }

}
