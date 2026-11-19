package com.ognjen.diplomski.ai.lifecycle.training;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;

import com.ognjen.diplomski.ai.domain.CustomerFeatures;
import com.ognjen.diplomski.ai.model.DecisionTreeModel;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Builds the effective point-in-time dataset and decides whether it is new and trainable. */
@Service
public class AiTrainingEligibilityService {
  public record Result(
      boolean eligible,
      String reason,
      String fingerprint,
      int matureCount,
      int trainingCount,
      int evaluationCount,
      int newMatureCount,
      int[] distribution,
      DecisionTreeModel.Prepared prepared) {}

  private final RiskStore store;
  private final int maxRetries;

  public AiTrainingEligibilityService(
      RiskStore store, @Value("${ai.lifecycle.max-retries:3}") int maxRetries) {
    this.store = store;
    this.maxRetries = maxRetries;
  }

  public Result check(Instant now) {
    long snapshotCount =
        Objects.requireNonNull(
            store.db().queryForObject("select count(*) from ai_risk_snapshot", Long.class));
    if (snapshotCount > 50_000)
      return skipped("Snapshot dataset exceeds the 50000-row training limit", 0);
    long orderCount =
        Objects.requireNonNull(
            store.db().queryForObject("select count(*) from ai_order_observation", Long.class));
    if (orderCount > 500_000)
      return skipped("Order observations exceed the 500000-row training limit", 0);

    var captures =
        store
            .db()
            .query(
                "select at from ai_capture_run order by at",
                (r, n) -> r.getTimestamp(1).toInstant());
    var orders =
        store.orders(0, Long.MAX_VALUE).stream()
            .collect(java.util.stream.Collectors.groupingBy(Order::customerId));
    var examples =
        store
            .db()
            .query(
                "select customer_id,at,features from ai_risk_snapshot order by at,customer_id",
                (r, n) -> {
                  var base =
                      new Example(
                          r.getLong(1),
                          r.getTimestamp(2).toInstant(),
                          store.read(r.getString(3), Features.class),
                          0);
                  return new Example(
                      base.customerId(),
                      base.at(),
                      base.features(),
                      CustomerFeatures.label(
                          base, orders.getOrDefault(base.customerId(), List.of())));
                })
            .stream()
            .filter(e -> CaptureCoverage.covers(e.at(), captures))
            .toList();
    try {
      var prepared = DecisionTreeModel.prepare(examples, now);
      String fingerprint = TrainingDatasetFingerprint.create(prepared);
      int[] distribution = new int[3];
      prepared.selected().forEach(e -> distribution[e.label()]++);
      Integer previous =
          store
              .db()
              .query(
                  "select max(mature_count) from ai_training_dataset where status in"
                      + " ('EVALUATED','PROMOTED','REJECTED','FAILED')",
                  r -> r.next() ? (Integer) r.getObject(1) : null);
      int newCount = Math.max(0, prepared.selected().size() - (previous == null ? 0 : previous));
      var existing =
          store
              .db()
              .queryForList(
                  "select status,attempts,retry_after from ai_training_dataset where fingerprint=?",
                  fingerprint);
      if (!existing.isEmpty()) {
        var row = existing.getFirst();
        boolean retryable =
            "FAILED_RETRYABLE".equals(row.get("status"))
                && ((Number) row.get("attempts")).intValue() < maxRetries
                && row.get("retry_after") != null
                && !((java.sql.Timestamp) row.get("retry_after")).toInstant().isAfter(now);
        if (!retryable)
          return new Result(
              false,
              "Podaci nisu promenjeni od prethodne obuke.",
              fingerprint,
              prepared.selected().size(),
              prepared.training().size(),
              prepared.evaluation().size(),
              0,
              distribution,
              prepared);
      }
      Long running =
          store
              .db()
              .queryForObject(
                  "select count(*) from ai_job_execution where job_type='TRAINING' and status='RUNNING'",
                  Long.class);
      if (running != null && running > 0)
        return new Result(
            false,
            "Drugi proces obuke je već u toku.",
            fingerprint,
            prepared.selected().size(),
            prepared.training().size(),
            prepared.evaluation().size(),
            newCount,
            distribution,
            prepared);
      return new Result(
          true,
          "Novi sazreli istorijski primeri ispunjavaju uslove za obuku.",
          fingerprint,
          prepared.selected().size(),
          prepared.training().size(),
          prepared.evaluation().size(),
          newCount,
          distribution,
          prepared);
    } catch (IllegalStateException error) {
      return skipped(error.getMessage(), examples.size());
    }
  }

  private static Result skipped(String reason, int mature) {
    return new Result(false, reason, null, mature, 0, 0, 0, new int[3], null);
  }

}
