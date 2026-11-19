package com.ognjen.diplomski.ai.lifecycle.training;

import static com.ognjen.diplomski.ai.support.AiErrorMessages.safe;

import com.ognjen.diplomski.ai.audit.AiAuditLogger;
import com.ognjen.diplomski.ai.lifecycle.model.AiModelService;
import com.ognjen.diplomski.ai.model.DecisionTreeArtifactCodec;
import com.ognjen.diplomski.ai.model.DecisionTreeModel;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import com.ognjen.diplomski.ai.service.AiAssessmentService;
import com.ognjen.diplomski.ai.service.AiOperationalStatusService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Eligibility orchestration, durable execution lease, training, evaluation and promotion. */
@Service
public class AiTrainingService {
  private final RiskStore store;
  private final AiTrainingEligibilityService eligibility;
  private final AiModelService models;
  private final AiAssessmentService assessments;
  private final TrainingExecutionCoordinator executions;
  private final AiOperationalStatusService health;
  private final AiAuditLogger audit;
  private final int maxRetries;

  public AiTrainingService(
      RiskStore store,
      AiTrainingEligibilityService eligibility,
      AiModelService models,
      AiAssessmentService assessments,
      TrainingExecutionCoordinator executions,
      AiOperationalStatusService health,
      AiAuditLogger audit,
      @Value("${ai.lifecycle.max-retries:3}") int maxRetries) {
    this.store = store;
    this.eligibility = eligibility;
    this.models = models;
    this.assessments = assessments;
    this.executions = executions;
    this.health = health;
    this.audit = audit;
    this.maxRetries = maxRetries;
  }

  public AiTrainingEligibilityService.Result checkAndTrain(String scheduleKey) {
    long started = System.nanoTime();
    UUID eligibilityJob = executions.startOnce("ELIGIBILITY", null, scheduleKey);
    if (eligibilityJob == null)
      return new AiTrainingEligibilityService.Result(
          false, "Nedeljni termin je veÄ‡ obraÄ‘en.", null, 0, 0, 0, 0, new int[3], null);
    AiTrainingEligibilityService.Result result;
    try {
      result = eligibility.check(Instant.now());
      store
          .db()
          .update(
              "update ai_system_state set last_eligibility_check_at=now(),eligible_sample_count=?,"
                  + "new_mature_sample_count=?,operational_status=case when persistent_error is"
                  + " null then 'RUNNING' else 'DEGRADED' end,updated_at=now()"
                  + " where singleton",
              result.matureCount(),
              result.newMatureCount());
      executions.finish(
          eligibilityJob,
          result.eligible() ? "COMPLETED" : "SKIPPED",
          result.reason(),
          store.json(
              Map.of(
                  "mature", result.matureCount(),
                  "training", result.trainingCount(),
                  "evaluation", result.evaluationCount())),
          null);
      health.recover(AiOperationalStatusService.Area.TRAINING);
      var eligibilityDetails = new LinkedHashMap<String, Object>();
      eligibilityDetails.put("scheduleKey", scheduleKey);
      eligibilityDetails.put("reason", result.reason());
      eligibilityDetails.put("datasetFingerprint", result.fingerprint());
      eligibilityDetails.put("datasetProvenance", "UNVERIFIED");
      eligibilityDetails.put("matureSamples", result.matureCount());
      eligibilityDetails.put("trainingSamples", result.trainingCount());
      eligibilityDetails.put("evaluationSamples", result.evaluationCount());
      eligibilityDetails.put("classDistribution", result.distribution());
      audit.training(
          result.eligible() ? "TRAINING_ELIGIBLE" : "TRAINING_SKIPPED",
          AiAuditLogger.Severity.INFO,
          new AiAuditLogger.Context(
              scheduleKey, eligibilityJob, null, null, null,
              result.eligible() ? "ELIGIBLE" : "SKIPPED",
              (System.nanoTime() - started) / 1_000_000),
          eligibilityDetails);
    } catch (Exception error) {
      executions.finish(eligibilityJob, "FAILED", "Provera uslova nije uspela.", null, safe(error));
      executions.recordPersistentError(error);
      health.fail(AiOperationalStatusService.Area.TRAINING, safe(error));
      audit.error(
          "TRAINING_ELIGIBILITY_FAILED",
          new AiAuditLogger.Context(
              scheduleKey, eligibilityJob, null, null, null, "FAILED",
              (System.nanoTime() - started) / 1_000_000),
          "TRAINING_ELIGIBILITY",
          error,
          Map.of("scheduleKey", scheduleKey));
      return new AiTrainingEligibilityService.Result(
          false, safe(error), null, 0, 0, 0, 0, new int[3], null);
    }
    if (result.eligible()) train(result);
    return result;
  }

  private void train(AiTrainingEligibilityService.Result initial) {
    long started = System.nanoTime();
    UUID owner = UUID.randomUUID();
    if (!executions.acquire(owner)) return;
    UUID trainingJob = null;
    String fingerprint = initial.fingerprint();
    try {
      var current = eligibility.check(Instant.now());
      if (!current.eligible() || !Objects.equals(fingerprint, current.fingerprint())) return;
      fingerprint = current.fingerprint();
      trainingJob = executions.start("TRAINING", fingerprint);
      audit.training(
          "TRAINING_STARTED",
          AiAuditLogger.Severity.INFO,
          new AiAuditLogger.Context(
              fingerprint, trainingJob, null, null, null, "RUNNING", null),
          Map.of(
              "datasetFingerprint", fingerprint,
              "datasetProvenance", "UNVERIFIED",
              "trainingSamples", current.trainingCount(),
              "evaluationSamples", current.evaluationCount(),
              "classDistribution", current.distribution(),
              "parameters", Map.of("splitRule", "GINI", "maxDepth", 5, "maxNodes", 16, "nodeSize", 10)));
      int updated =
          store
              .db()
              .update(
                  "update ai_training_dataset set status='RUNNING',attempts=attempts+1,"
                      + "retry_after=null,decision_reason=null where fingerprint=?"
                      + " and status='FAILED_RETRYABLE' and attempts<?",
                  fingerprint,
                  maxRetries);
      if (updated == 0)
        store
            .db()
            .update(
                "insert into ai_training_dataset(fingerprint,status,mature_count,training_count,"
                    + "evaluation_count,class_distribution,first_snapshot_at,last_snapshot_at)"
                    + " values (?,'RUNNING',?,?,?,?,?,?)",
                fingerprint,
                current.matureCount(),
                current.trainingCount(),
                current.evaluationCount(),
                store.json(current.distribution()),
                Timestamp.from(current.prepared().selected().getFirst().at()),
                Timestamp.from(current.prepared().selected().getLast().at()));

      var candidate = DecisionTreeModel.train(current.prepared(), Instant.now());
      byte[] artifact = DecisionTreeModel.save(candidate.tree());
      var restored = DecisionTreeModel.load(artifact);
      for (var example : current.prepared().selected())
        if (DecisionTreeModel.predict(candidate.tree(), example.features())
            != DecisionTreeModel.predict(restored, example.features()))
          throw new IllegalStateException("Artifact round-trip mismatch");
      UUID version = UUID.randomUUID();
      String provenance = "UNVERIFIED";
      String modelVersion =
          store
          .db()
          .queryForObject(
              "insert into ai_model(version,trained_at,schema_version,artifact,sha256,metrics,"
                  + "eligible,active,provenance,dataset_fingerprint,model_configuration) values"
                  + " (?,now(),1,?,?,?,?,false,?,?,?) returning model_version",
              String.class,
              version,
              artifact,
              DecisionTreeArtifactCodec.sha256(artifact),
              store.json(candidate.evaluation()),
              candidate.evaluation().eligible(),
              provenance,
              fingerprint,
              store.json(Map.of("algorithm", "Smile 4.4.0 CART", "splitRule", "GINI", "maxDepth", 5, "maxNodes", 16, "nodeSize", 10)));
      var promotion =
          models.promote(
              version,
              candidate.tree(),
              candidate.evaluation(),
              current.prepared().evaluation(),
              provenance);
      store
          .db()
          .update(
              "update ai_training_dataset set status=?,completed_at=now(),model_version=?,"
                  + "decision_reason=? where fingerprint=?",
              promotion.promoted() ? "PROMOTED" : "REJECTED",
              version,
              promotion.reason(),
              fingerprint);
      store
          .db()
          .update(
              "update ai_system_state set last_training_attempt_at=now(),last_training_outcome=?,"
                  + "last_promotion_decision=?,last_promotion_reason=?,"
                  + "updated_at=now() where singleton",
              candidate.evaluation().eligible() ? "EVALUATED" : "REJECTED",
              promotion.decision(),
              promotion.reason());
      executions.finish(
          trainingJob,
          "COMPLETED",
          promotion.reason(),
          store.json(Map.of("version", version, "evaluation", candidate.evaluation())),
          null);
      var completedDetails = new LinkedHashMap<String, Object>();
      completedDetails.put("datasetFingerprint", fingerprint);
      completedDetails.put("datasetProvenance", provenance);
      completedDetails.put("evaluation", candidate.evaluation());
      completedDetails.put("candidateMetrics", candidate.evaluation().tree());
      completedDetails.put("baselineMetrics", candidate.evaluation().baseline());
      completedDetails.put("incumbentMetrics", promotion.incumbentMetrics());
      completedDetails.put("promotionDecision", promotion.decision());
      completedDetails.put("decisionReason", promotion.reason());
      completedDetails.put("previousModelId", promotion.previousModel());
      audit.training(
          promotion.promoted() ? "MODEL_PROMOTED" : "MODEL_REJECTED",
          promotion.promoted() ? AiAuditLogger.Severity.INFO : AiAuditLogger.Severity.WARN,
          new AiAuditLogger.Context(
              fingerprint, trainingJob, version, modelVersion, null,
              promotion.promoted() ? "PROMOTED" : "REJECTED",
              (System.nanoTime() - started) / 1_000_000),
          completedDetails);
      if (promotion.promoted())
        audit.lifecycle(
            "MODEL_ACTIVATED",
            AiAuditLogger.Severity.INFO,
            new AiAuditLogger.Context(
                fingerprint, trainingJob, version, modelVersion, null, "PROMOTED",
                (System.nanoTime() - started) / 1_000_000),
            Map.of("reason", promotion.reason()));
      if (promotion.promoted()) assessments.refreshAll(Instant.now(), false, "MODEL_PROMOTION");
      health.recover(AiOperationalStatusService.Area.TRAINING);
    } catch (Exception error) {
      int attempts = executions.attempts(fingerprint);
      boolean retryable = error instanceof DataAccessException && attempts < maxRetries;
      if (fingerprint != null)
        store
            .db()
            .update(
                "update ai_training_dataset set status=?,completed_at=now(),retry_after=?,"
                    + "decision_reason=? where fingerprint=?",
                retryable ? "FAILED_RETRYABLE" : "FAILED",
                retryable ? Timestamp.from(Instant.now().plus(Duration.ofMinutes(10))) : null,
                safe(error),
                fingerprint);
      if (trainingJob != null)
        executions.finish(trainingJob, "FAILED", "Automatska obuka nije uspela.", null, safe(error));
      executions.recordPersistentError(error);
      health.fail(AiOperationalStatusService.Area.TRAINING, safe(error));
      audit.error(
          "TRAINING_FAILED",
          new AiAuditLogger.Context(
              fingerprint, trainingJob, null, null, null, "FAILED",
              (System.nanoTime() - started) / 1_000_000),
          "TRAINING",
          error,
          Map.of("datasetFingerprint", Objects.toString(fingerprint, "UNKNOWN")));
      LoggerFactory.getLogger(AiTrainingService.class).error("Autonomous AI training failed", error);
    } finally {
      executions.release(owner);
    }
  }

  public void recoverAbandonedWork() {
    executions.recoverAbandonedWork();
  }

  public void recordMissedSchedule(String scheduleKey, Instant scheduledAt) {
    if (executions.recordMissed("ELIGIBILITY", scheduleKey, scheduledAt))
      audit.lifecycle(
          "WEEKLY_TRAINING_SCHEDULE_MISSED",
          AiAuditLogger.Severity.WARN,
          AiAuditLogger.Context.empty("MISSED"),
          Map.of("scheduleKey", scheduleKey, "scheduledAt", scheduledAt));
  }
}
