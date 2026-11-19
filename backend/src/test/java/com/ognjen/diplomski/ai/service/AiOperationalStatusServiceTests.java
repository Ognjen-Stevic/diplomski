package com.ognjen.diplomski.ai.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ognjen.diplomski.ai.lifecycle.model.AiModelService;
import com.ognjen.diplomski.ai.audit.AiAuditLogger;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiOperationalStatusServiceTests {
  private RiskStore store;
  private JdbcTemplate db;
  private AiModelService models;

  @BeforeEach
  void setUp() {
    store = mock(RiskStore.class);
    db = mock(JdbcTemplate.class);
    models = mock(AiModelService.class);
    when(store.db()).thenReturn(db);
    UUID version = UUID.randomUUID();
    when(models.availability())
        .thenReturn(
            new AiModelService.Availability(
                version,
                "UNVERIFIED",
                new AiModelService.Active(version, "V001", null, "UNVERIFIED"),
                null));
  }

  @Test
  void healthySchedulerModelAndProcessingReportRunning() {
    when(db.queryForMap(anyString())).thenReturn(state(null, null));

    new AiOperationalStatusService(store, models, mock(AiAuditLogger.class), true, 60_000).refresh();

    verify(db).update(contains("operational_status"), eq("RUNNING"), isNull());
  }

  @Test
  void unresolvedAssessmentFailureReportsDegraded() {
    when(db.queryForMap(anyString()))
        .thenReturn(state("ASSESSMENT_REFRESH_FAILED: customer 42", null));

    new AiOperationalStatusService(store, models, mock(AiAuditLogger.class), true, 60_000).refresh();

    verify(db)
        .update(
            contains("operational_status"),
            eq("DEGRADED"),
            eq("ASSESSMENT_REFRESH_FAILED: customer 42"));
  }

  @Test
  void confirmedAssessmentRecoveryReturnsToRunning() {
    when(db.queryForObject(contains("ai_assessment_work"), eq(Long.class))).thenReturn(0L);
    when(db.queryForMap(anyString())).thenReturn(state(null, null));
    var health =
        new AiOperationalStatusService(store, models, mock(AiAuditLogger.class), true, 60_000);

    health.recover(AiOperationalStatusService.Area.ASSESSMENT);

    verify(db).update(contains("assessment_error=null"));
    verify(db).update(contains("operational_status"), eq("RUNNING"), isNull());
  }

  @Test
  void assessmentRecoveryDoesNotClearUnrelatedError() {
    when(db.queryForObject(contains("ai_assessment_work"), eq(Long.class))).thenReturn(0L);
    when(db.queryForMap(anyString())).thenReturn(state(null, "CAPTURE_DATABASE_FAILURE"));
    var health =
        new AiOperationalStatusService(store, models, mock(AiAuditLogger.class), true, 60_000);

    health.recover(AiOperationalStatusService.Area.ASSESSMENT);

    verify(db).update(contains("assessment_error=null"));
    verify(db)
        .update(contains("operational_status"), eq("DEGRADED"), eq("CAPTURE_DATABASE_FAILURE"));
    verify(db, never()).update(contains("other_error=null"));
  }

  @Test
  void skippedEligibilityAndRejectedCandidateHistoryDoNotDegradeCurrentHealth() {
    when(db.queryForMap(anyString())).thenReturn(state(null, null));

    new AiOperationalStatusService(store, models, mock(AiAuditLogger.class), true, 60_000).refresh();

    verify(db).update(contains("operational_status"), eq("RUNNING"), isNull());
  }

  private static Map<String, Object> state(String assessmentError, String otherError) {
    Map<String, Object> state = new HashMap<>();
    state.put("operational_status", "RUNNING");
    state.put("scheduler_heartbeat_at", Timestamp.from(Instant.now()));
    state.put("assessment_error", assessmentError);
    state.put("capture_error", null);
    state.put("snapshot_error", null);
    state.put("training_error", null);
    state.put("other_error", otherError);
    return state;
  }
}
