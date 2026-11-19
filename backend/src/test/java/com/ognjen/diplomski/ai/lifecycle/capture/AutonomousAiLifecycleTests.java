package com.ognjen.diplomski.ai.lifecycle.capture;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ognjen.diplomski.ai.audit.AiAuditLogger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.ognjen.diplomski.ai.lifecycle.training.AiTrainingEligibilityService;
import com.ognjen.diplomski.ai.lifecycle.training.CaptureCoverage;
import com.ognjen.diplomski.ai.lifecycle.training.TrainingDatasetFingerprint;
import com.ognjen.diplomski.ai.lifecycle.model.AiModelService;
import com.ognjen.diplomski.ai.model.DecisionTreeModel;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import com.ognjen.diplomski.ai.service.AiAssessmentService;
import com.ognjen.diplomski.ai.service.AiOperationalStatusService;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class AutonomousAiLifecycleTests {
  private static Features features(int label) {
    return new Features(5, 7, 5, 10, 15, 100 + label * 100, 100, 400);
  }

  private static DecisionTreeModel.Prepared prepared(double changedValue) {
    Instant start = Instant.parse("2025-01-01T00:00:00Z");
    var training = new ArrayList<Example>();
    var evaluation = new ArrayList<Example>();
    for (int i = 0; i < 120; i++) {
      Features f = features(i % 3);
      if (i == 0 && changedValue != 0)
        f = new Features(changedValue, 7, 5, 10, 15, 100, 100, 400);
      training.add(new Example(i + 1, start, f, i % 3));
    }
    for (int i = 0; i < 60; i++)
      evaluation.add(
          new Example(i + 121, start.plus(120, ChronoUnit.DAYS), features(i % 3), i % 3));
    var selected = new ArrayList<Example>(training);
    selected.addAll(evaluation);
    return new DecisionTreeModel.Prepared(
        List.copyOf(selected),
        List.copyOf(training),
        List.copyOf(evaluation),
        start.plus(120, ChronoUnit.DAYS));
  }

  @Test
  void datasetFingerprintIsDeterministicAndCoversFeatureChangesAndSplit() {
    String first = TrainingDatasetFingerprint.create(prepared(0));
    assertEquals(first, TrainingDatasetFingerprint.create(prepared(0)));
    assertNotEquals(first, TrainingDatasetFingerprint.create(prepared(99)));
    var original = prepared(0);
    var changedSplit =
        new DecisionTreeModel.Prepared(
            original.selected(),
            original.training().subList(0, 119),
            original.evaluation(),
            original.evaluationStart());
    assertNotEquals(first, TrainingDatasetFingerprint.create(changedSplit));
  }

  @Test
  void incompleteCoverageNeverTurnsMissingObservationsIntoHighLabels() {
    Instant start = Instant.parse("2025-01-01T00:00:00Z");
    assertFalse(CaptureCoverage.covers(start, List.of()));
    var captures = new ArrayList<Instant>();
    for (int day = 1; day <= 60; day++) captures.add(start.plus(day, ChronoUnit.DAYS));
    assertTrue(CaptureCoverage.covers(start, captures));
    captures.remove(20);
    assertFalse(CaptureCoverage.covers(start, captures));
  }

  @Test
  void outboxInsertionIsIdempotentAtDatabaseBoundary() {
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(store.db()).thenReturn(db);
    AiCaptureOutbox outbox = new AiCaptureOutbox(store);
    outbox.enqueue(42L);
    outbox.enqueue(42L);
    verify(db, times(2))
        .update(contains("on conflict(requisition_id) do nothing"), eq(42L));
  }

  @Test
  void noSnapshotsProducesNormalEligibilitySkip() {
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(store.db()).thenReturn(db);
    when(db.queryForObject(contains("ai_risk_snapshot"), eq(Long.class))).thenReturn(0L);
    when(db.queryForObject(contains("ai_order_observation"), eq(Long.class))).thenReturn(0L);
    when(db.query(contains("ai_capture_run"), any(org.springframework.jdbc.core.RowMapper.class)))
        .thenReturn(List.of());
    when(store.orders(0, Long.MAX_VALUE)).thenReturn(List.of());
    when(db.query(contains("ai_risk_snapshot order"), any(org.springframework.jdbc.core.RowMapper.class)))
        .thenReturn(List.of());

    var result = new AiTrainingEligibilityService(store, 3).check(Instant.now());

    assertFalse(result.eligible());
    assertTrue(result.reason().contains("150"));
  }

  @Test
  void capturedOrderCompletesOnlyAfterDurableAssessmentWorkIsQueued() throws Exception {
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    AiAssessmentService assessments = mock(AiAssessmentService.class);
    AiOperationalStatusService health = mock(AiOperationalStatusService.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    TransactionStatus transaction = mock(TransactionStatus.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<PlatformTransactionManager> provider = mock(ObjectProvider.class);
    when(store.db()).thenReturn(db);
    when(provider.getObject()).thenReturn(transactions);
    when(transactions.getTransaction(any())).thenReturn(transaction);
    var event = new HashMap<String, Object>();
    event.put("id", 7L);
    event.put("requisition_id", 99L);
    event.put("created_at", Timestamp.from(Instant.now()));
    event.put("attempts", 0);
    when(db.queryForList(contains("for update"), eq(1))).thenReturn(List.of(event));
    when(db.query(contains("select customer_id"), any(ResultSetExtractor.class), eq(99L)))
        .thenReturn(42L);
    var service =
        new AiCaptureService(store, assessments, provider, health, mock(AiAuditLogger.class), 3);

    assertEquals(1, service.processBatch(1));

    verify(db).update(contains("status='COMPLETED'"), eq(7L));
    verify(assessments).enqueue(42L, 99L, "REQUISITION_COMPLETED", false);
    verify(assessments, never()).refreshCustomer(anyLong(), any(), anyBoolean());
    verify(db, never()).update(contains("available_at=now()"), any(), any(), any());
  }

  @Test
  void captureJobIsFailedWhenItsQueuedAssessmentAttemptFails() {
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    AiAssessmentService assessments = mock(AiAssessmentService.class);
    AiOperationalStatusService health = mock(AiOperationalStatusService.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    TransactionStatus transaction = mock(TransactionStatus.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<PlatformTransactionManager> provider = mock(ObjectProvider.class);
    when(store.db()).thenReturn(db);
    when(provider.getObject()).thenReturn(transactions);
    when(transactions.getTransaction(any())).thenReturn(transaction);
    when(db.queryForList(contains("for update"), eq(100))).thenReturn(List.of());
    when(db.queryForList(contains("status='FAILED' order by id"))).thenReturn(List.of());
    when(assessments.processPending(any(), eq(100)))
        .thenReturn(new AiAssessmentService.BatchResult(1, 0, 1));
    when(assessments.refreshStale(any()))
        .thenReturn(new AiAssessmentService.BatchResult(0, 0, 0));
    var service =
        new AiCaptureService(store, assessments, provider, health, mock(AiAuditLogger.class), 3);

    service.reconcileAndProcess();

    verify(db)
        .update(
            contains("update ai_job_execution set status=?"),
            eq("FAILED"),
            anyString(),
            contains("ASSESSMENT_REFRESH_FAILED"),
            any(UUID.class));
  }

  @Test
  void failedAssessmentIsDurablyRetriedWithoutBlockingAnotherCustomer() throws Exception {
    AssessmentFixture fixture = assessmentFixture(0, 41L, 42L);
    doThrow(new IllegalStateException("customer 41 failed"))
        .when(fixture.service())
        .refreshCustomer(eq(41L), any(), eq(false), anyString());
    doNothing().when(fixture.service()).refreshCustomer(eq(42L), any(), eq(false), anyString());

    var result = fixture.service().processPending(Instant.now(), 100);

    assertEquals(2, result.attempted());
    assertEquals(1, result.succeeded());
    assertEquals(1, result.failed());
    verify(fixture.db())
        .update(
            contains("status=?"),
            eq("PENDING"),
            eq(5),
            contains("customer 41 failed"),
            eq(41L),
            eq(1L));
    verify(fixture.db())
        .update(contains("status='COMPLETED'"), eq(42L), eq(1L));
    verify(fixture.health())
        .fail(
            eq(AiOperationalStatusService.Area.ASSESSMENT),
            contains("customer 41 failed"));
  }

  @Test
  void retryAttemptsPersistAcrossServiceRestartAndBecomeTerminalAtTheBound() throws Exception {
    AssessmentFixture fixture = assessmentFixture(2, 55L);
    doThrow(new IllegalStateException("still failing"))
        .when(fixture.service())
        .refreshCustomer(eq(55L), any(), eq(false), anyString());

    fixture.service().processPending(Instant.now(), 100);

    verify(fixture.db())
        .update(
            contains("status=?"),
            eq("FAILED"),
            eq(15),
            eq("still failing"),
            eq(55L),
            eq(1L));
  }

  @Test
  void successfulRetryConfirmsRecoveryBeforeHealthIsCleared() throws Exception {
    AssessmentFixture fixture = assessmentFixture(1, 88L);
    doNothing().when(fixture.service()).refreshCustomer(eq(88L), any(), eq(false), anyString());

    fixture.service().processPending(Instant.now(), 100);

    verify(fixture.db())
        .update(contains("status='COMPLETED'"), eq(88L), eq(1L));
    verify(fixture.health()).recover(AiOperationalStatusService.Area.ASSESSMENT);
  }

  @Test
  void pendingAssessmentBatchLoadsSharedInputsAndModelOnce() throws Exception {
    AssessmentFixture fixture = assessmentFixture(0, 41L, 42L);
    doNothing().when(fixture.service()).refreshCustomer(anyLong(), any(), eq(false), anyString());

    fixture.service().processPending(Instant.now(), 100);

    verify(fixture.db())
        .queryForList(contains("select c.id,c.created_at"), eq(41L), eq(42L));
    verify(fixture.store()).orders(List.of(41L, 42L));
    verify(fixture.models()).availability();
  }

  private static AssessmentFixture assessmentFixture(int attempts, long... customerIds) {
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    AiModelService models = mock(AiModelService.class);
    AiOperationalStatusService health = mock(AiOperationalStatusService.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    TransactionStatus transaction = mock(TransactionStatus.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<PlatformTransactionManager> provider = mock(ObjectProvider.class);
    when(store.db()).thenReturn(db);
    when(provider.getObject()).thenReturn(transactions);
    when(transactions.getTransaction(any())).thenReturn(transaction);
    var rows = new ArrayList<Map<String, Object>>();
    for (long customerId : customerIds) {
      Map<String, Object> row = new HashMap<>();
      row.put("customer_id", customerId);
      row.put("generation", 1L);
      row.put("attempts", attempts);
      row.put("snapshot", false);
      rows.add(row);
    }
    when(db.queryForList(contains("ai_assessment_work"), eq(100))).thenReturn(rows);
    var service =
        spy(new AiAssessmentService(store, models, provider, health, mock(AiAuditLogger.class), 3));
    return new AssessmentFixture(service, db, health, store, models);
  }

  private record AssessmentFixture(
      AiAssessmentService service,
      JdbcTemplate db,
      AiOperationalStatusService health,
      RiskStore store,
      AiModelService models) {}
}
