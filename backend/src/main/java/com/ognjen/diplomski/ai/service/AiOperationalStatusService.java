package com.ognjen.diplomski.ai.service;

import com.ognjen.diplomski.ai.audit.AiAuditLogger;
import com.ognjen.diplomski.ai.lifecycle.model.AiModelService;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Maintains current AI health without treating expected skips or model rejections as failures. */
@Service
public class AiOperationalStatusService {
  public enum Area {
    ASSESSMENT("assessment_error"),
    CAPTURE("capture_error"),
    SNAPSHOT("snapshot_error"),
    TRAINING("training_error");

    private final String column;

    Area(String column) {
      this.column = column;
    }
  }

  private final RiskStore store;
  private final AiModelService models;
  private final boolean schedulingEnabled;
  private final Duration heartbeatTolerance;
  private final AiAuditLogger audit;

  public AiOperationalStatusService(
      RiskStore store,
      AiModelService models,
      AiAuditLogger audit,
      @Value("${ai.lifecycle.scheduling.enabled:true}") boolean schedulingEnabled,
      @Value("${ai.lifecycle.capture-delay-ms:60000}") long captureDelayMs) {
    this.store = store;
    this.models = models;
    this.audit = audit;
    this.schedulingEnabled = schedulingEnabled;
    this.heartbeatTolerance = Duration.ofMillis(Math.max(180_000L, captureDelayMs * 3 + 30_000L));
  }

  public void heartbeat() {
    store
        .db()
        .update("update ai_system_state set scheduler_heartbeat_at=now(),updated_at=now() where singleton");
    refresh();
  }

  public void fail(Area area, String message) {
    store
        .db()
        .update(
            "update ai_system_state set "
                + area.column
                + "=?,updated_at=now() where singleton",
            message);
    refresh();
  }

  public void recover(Area area) {
    if (area == Area.ASSESSMENT) {
      Long unresolved =
          store
              .db()
              .queryForObject(
                  "select count(*) from ai_assessment_work where status='FAILED'"
                      + " or (status in ('PENDING','PROCESSING') and last_error is not null)",
                  Long.class);
      if (unresolved != null && unresolved > 0) {
        refresh();
        return;
      }
    }
    store
        .db()
        .update(
            "update ai_system_state set "
                + area.column
                + "=null,updated_at=now() where singleton");
    refresh();
  }

  public Map<String, Object> refresh() {
    var state =
        store
            .db()
            .queryForMap(
                "select operational_status,scheduler_heartbeat_at,assessment_error,capture_error,snapshot_error,"
                    + "training_error,other_error from ai_system_state where singleton");
    var availability = models.availability();
    String problem = null;
    if (!schedulingEnabled) problem = "AI_SCHEDULER_DISABLED";
    else {
      Object heartbeat = state.get("scheduler_heartbeat_at");
      if (heartbeat == null
          || toInstant(heartbeat).isBefore(Instant.now().minus(heartbeatTolerance)))
        problem = "AI_SCHEDULER_HEARTBEAT_MISSING";
    }
    if (!availability.usable())
      problem = "MODEL_UNAVAILABLE: " + availability.unavailableReason();
    problem = first(problem, state.get("assessment_error"));
    problem = first(problem, state.get("capture_error"));
    problem = first(problem, state.get("snapshot_error"));
    problem = first(problem, state.get("training_error"));
    problem = first(problem, state.get("other_error"));
    store
        .db()
        .update(
            "update ai_system_state set operational_status=?,persistent_error=?,updated_at=now()"
                + " where singleton",
            problem == null ? "RUNNING" : "DEGRADED",
            problem);
    String previous = Objects.toString(state.get("operational_status"), "STARTING");
    String current = problem == null ? "RUNNING" : "DEGRADED";
    if (!previous.equals(current)) {
      var details = new java.util.LinkedHashMap<String, Object>();
      details.put("previousStatus", previous);
      details.put("currentStatus", current);
      details.put("reason", problem == null ? "RECOVERED" : problem);
      audit.lifecycle(
          "OPERATIONAL_STATUS_CHANGED",
          problem == null ? AiAuditLogger.Severity.INFO : AiAuditLogger.Severity.WARN,
          AiAuditLogger.Context.empty(current),
          details);
    }
    return state;
  }

  private static String first(String current, Object candidate) {
    return current != null ? current : Objects.toString(candidate, null);
  }

  private static Instant toInstant(Object value) {
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
    if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
    if (value instanceof Instant instant) return instant;
    throw new IllegalStateException("Unsupported scheduler heartbeat value: " + value.getClass());
  }
}
