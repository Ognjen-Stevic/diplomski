package com.ognjen.diplomski.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RiskTypes {
  private RiskTypes() {}

  public enum Level {
    LOW,
    MEDIUM,
    HIGH,
    INSUFFICIENT_DATA
  }

  public record Order(long customerId, Instant createdAt, Instant observedAt, double liters) {}

  public record Features(
      double recencyDays,
      double intervalDays,
      double orders30,
      double orders60,
      double orders90,
      double liters60,
      double priorLiters60,
      double relationshipDays) {
    public static final String[] NAMES = {
      "recencyDays",
      "intervalDays",
      "orders30",
      "orders60",
      "orders90",
      "liters60",
      "priorLiters60",
      "relationshipDays"
    };

    public double[] vector() {
      return new double[] {
        recencyDays,
        intervalDays,
        orders30,
        orders60,
        orders90,
        liters60,
        priorLiters60,
        relationshipDays
      };
    }

    public boolean sufficient() {
      return relationshipDays >= 90 && orders90 >= 3 && liters60 > 0;
    }
  }

  public record Example(long customerId, Instant at, Features features, int label) {}

  public record Metrics(
      int count,
      int[][] confusion,
      double[] precision,
      double[] recall,
      double[] f1,
      double macroF1,
      int[] distribution) {}

  public record Evaluation(
      int trainingCount,
      Metrics tree,
      Metrics baseline,
      boolean eligible,
      String reason,
      Instant trainOutcomeEnd,
      Instant evaluationStart,
      Instant observedThrough,
      String algorithm) {}

  public record Assessment(
      Level level,
      Instant at,
      String status,
      Features features,
      List<String> factors,
      String recommendation,
      boolean stale) {}

  public record RiskListItem(
      long customerId,
      String customerName,
      Level level,
      Instant at,
      String recommendation,
      boolean stale) {}

  public record RiskBatchItem(long customerId, Level level) {}

  public record RiskPage(List<RiskListItem> content, long total, int page, int size) {}

  public record Summary(
      long total,
      Map<String, Long> distribution,
      long increased,
      Double highRiskPercentage,
      List<Map<String, Object>> teams) {}

  public record ActivityRequest(
      @jakarta.validation.constraints.NotBlank String type,
      @jakarta.validation.constraints.Size(max = 2000) String notes,
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 1000)
          String outcome) {}

  public record ActivityItem(long id, String type, Instant at, String outcome) {}
}
