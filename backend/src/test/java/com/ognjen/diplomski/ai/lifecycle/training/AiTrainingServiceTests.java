package com.ognjen.diplomski.ai.lifecycle.training;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ognjen.diplomski.ai.lifecycle.model.AiModelService;
import com.ognjen.diplomski.ai.audit.AiAuditLogger;
import com.ognjen.diplomski.ai.model.DecisionTreeModel;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import com.ognjen.diplomski.ai.service.AiAssessmentService;
import com.ognjen.diplomski.ai.service.AiOperationalStatusService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiTrainingServiceTests {
  @Test
  void expectedCandidateRejectionDoesNotDegradeHealth() throws Exception {
    Fixture fixture =
        fixture(
            new AiModelService.Promotion(
                false, "KEPT_ACTIVE_MODEL", "No improvement", UUID.randomUUID(), null));

    fixture.service().checkAndTrain("WEEKLY_TRAINING:2026-11-08");

    verify(fixture.health(), never()).fail(any(), anyString());
    verify(fixture.health(), atLeastOnce()).recover(AiOperationalStatusService.Area.TRAINING);
    verify(fixture.assessments(), never()).refreshAll(any(), anyBoolean(), anyString());
  }

  @Test
  void successfulPromotionImmediatelyTriggersFullCustomerReassessment() throws Exception {
    Fixture fixture =
        fixture(new AiModelService.Promotion(true, "PROMOTED", "Better model", null, null));

    fixture.service().checkAndTrain("WEEKLY_TRAINING:2026-11-08");

    verify(fixture.assessments()).refreshAll(any(), eq(false), eq("MODEL_PROMOTION"));
    verify(fixture.health(), never()).fail(any(), anyString());
  }

  private static Fixture fixture(AiModelService.Promotion promotion) throws Exception {
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    AiTrainingEligibilityService eligibility = mock(AiTrainingEligibilityService.class);
    AiModelService models = mock(AiModelService.class);
    AiAssessmentService assessments = mock(AiAssessmentService.class);
    TrainingExecutionCoordinator executions = mock(TrainingExecutionCoordinator.class);
    AiOperationalStatusService health = mock(AiOperationalStatusService.class);
    var result = eligibleResult();
    when(store.db()).thenReturn(db);
    when(eligibility.check(any())).thenReturn(result);
    when(executions.startOnce(eq("ELIGIBILITY"), isNull(), anyString())).thenReturn(UUID.randomUUID());
    when(executions.acquire(any())).thenReturn(true);
    when(executions.start(eq("TRAINING"), eq(result.fingerprint()))).thenReturn(UUID.randomUUID());
    when(models.promote(any(), any(), any(), anyList(), eq("UNVERIFIED"))).thenReturn(promotion);
    var service =
        new AiTrainingService(
            store, eligibility, models, assessments, executions, health, mock(AiAuditLogger.class), 3);
    return new Fixture(service, assessments, health);
  }

  private static AiTrainingEligibilityService.Result eligibleResult() {
    Instant start = Instant.parse("2025-01-01T00:00:00Z");
    List<Example> training = new ArrayList<>();
    List<Example> evaluation = new ArrayList<>();
    for (int index = 0; index < 120; index++)
      training.add(new Example(index + 1L, start.plus(index, ChronoUnit.DAYS), features(index % 3), index % 3));
    for (int index = 0; index < 60; index++)
      evaluation.add(new Example(index + 121L, start.plus(120L + index, ChronoUnit.DAYS), features(index % 3), index % 3));
    List<Example> selected = new ArrayList<>(training);
    selected.addAll(evaluation);
    var prepared =
        new DecisionTreeModel.Prepared(
            List.copyOf(selected), List.copyOf(training), List.copyOf(evaluation), evaluation.getFirst().at());
    return new AiTrainingEligibilityService.Result(
        true, "Eligible", "weekly-fingerprint", 180, 120, 60, 180, new int[] {60, 60, 60}, prepared);
  }

  private static Features features(int label) {
    double separator = 10 + label * 100;
    return new Features(separator, 7, 5, 10, 15, separator, 100, 400);
  }

  private record Fixture(
      AiTrainingService service,
      AiAssessmentService assessments,
      AiOperationalStatusService health) {}
}
