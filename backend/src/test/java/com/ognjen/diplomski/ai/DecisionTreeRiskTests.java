package com.ognjen.diplomski.ai;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ognjen.diplomski.ai.domain.CustomerFeatures;
import com.ognjen.diplomski.ai.lifecycle.training.CaptureCoverage;
import com.ognjen.diplomski.ai.model.DecisionTreeModel;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Explicit synthetic development fixture, never registered as a production model. */
class DecisionTreeRiskTests {
  static Features features(int label) {
    return new Features(5, 7, 5, 10, 15, 100 + label * 100, 100, 400);
  }

  @Test
  void pointInTimeFeaturesExcludeFutureAndNotYetObservedOrders() {
    Instant at = Instant.parse("2026-01-01T00:00:00Z");
    var orders =
        List.of(
            new Order(1, at.minusSeconds(86400), at.minusSeconds(1), 10),
            new Order(1, at.plusSeconds(1), at, 999),
            new Order(1, at.minusSeconds(10), at.plusSeconds(1), 999));
    var result = CustomerFeatures.extract(at, at.minus(100, ChronoUnit.DAYS), orders);
    assertEquals(1, result.orders30());
    assertEquals(10, result.liters60());
    assertEquals(1, result.recencyDays());
    assertFalse(result.sufficient());
  }

  @Test
  void labelsUseOnlyTheSubsequentMatureWindow() {
    Instant at = Instant.parse("2026-01-01T00:00:00Z");
    var e = new Example(1, at, features(0), 0);
    assertEquals(2, CustomerFeatures.label(e, List.of(new Order(1, at.minusSeconds(1), at, 1000))));
    assertEquals(
        1,
        CustomerFeatures.label(e, List.of(new Order(1, at.plusSeconds(1), at.plusSeconds(2), 25))));
    assertEquals(
        0,
        CustomerFeatures.label(
            e, List.of(new Order(1, at.plusSeconds(1), at.plusSeconds(2), 100))));
    assertEquals(
        2,
        CustomerFeatures.label(
            e, List.of(new Order(1, at.plusSeconds(1), at.plus(61, ChronoUnit.DAYS), 100))));
  }

  static List<Example> fixture() {
    var data = new ArrayList<Example>();
    Instant start = Instant.parse("2025-01-01T00:00:00Z");
    for (int period = 0; period < 3; period++)
      for (int i = 0; i < 150; i++)
        data.add(
            new Example(i + 1, start.plus(period * 120L, ChronoUnit.DAYS), features(i % 3), i % 3));
    return data;
  }

  @Test
  void realSmileTreeLearnsAndRoundTripsWithDecisionPaths() throws Exception {
    var candidate = DecisionTreeModel.train(fixture(), Instant.parse("2026-01-01T00:00:00Z"));
    assertTrue(candidate.evaluation().eligible());
    assertEquals(300, candidate.evaluation().trainingCount());
    assertEquals(150, candidate.evaluation().tree().count());
    var model = DecisionTreeModel.load(DecisionTreeModel.save(candidate.tree()));
    for (int c = 0; c < 3; c++) assertEquals(c, DecisionTreeModel.predict(model, features(c)));
    assertFalse(DecisionTreeModel.path(model, features(2)).isEmpty());
    var trace = DecisionTreeModel.trace(model, features(2));
    assertEquals(DecisionTreeModel.predict(model, features(2)), trace.prediction());
    assertFalse(trace.traversal().isEmpty());
    trace.traversal().forEach(
        step -> {
          assertTrue(List.of(Features.NAMES).contains(step.feature()));
          assertTrue(step.operator().equals("<=") || step.operator().equals(">"));
          assertTrue(step.selectedBranch().equals("TRUE") || step.selectedBranch().equals("FALSE"));
          assertTrue(Double.isFinite(step.threshold()));
        });
  }

  @Test
  void immatureOrInsufficientDataCannotTrain() {
    assertThrows(
        IllegalStateException.class, () -> DecisionTreeModel.train(List.of(), Instant.now()));
    assertThrows(
        IllegalStateException.class,
        () -> DecisionTreeModel.train(fixture(), Instant.parse("2025-01-02T00:00:00Z")));
  }

  @Test
  void overlappingSnapshotsDoNotInflateSampleCount() {
    var many = new ArrayList<Example>();
    Instant start = Instant.parse("2025-01-01T00:00:00Z");
    for (int i = 0; i < 500; i++)
      many.add(new Example(1, start.plus(i, ChronoUnit.HOURS), features(i % 3), i % 3));
    assertThrows(IllegalStateException.class, () -> DecisionTreeModel.train(many, Instant.now()));
  }

  @Test
  void evaluationReportsActualCountsAndBaseline() {
    var result = DecisionTreeModel.evaluate(fixture(), e -> 0);
    assertArrayEquals(new int[] {150, 150, 150}, result.distribution());
    assertEquals(1, result.recall()[0]);
    assertEquals(0, result.recall()[2]);
  }

  @Test
  void missingCaptureCoverageCannotBeLabeledAsInactivity() {
    Instant start = Instant.parse("2025-01-01T00:00:00Z");
    assertFalse(CaptureCoverage.covers(start, List.of()));
    var days = new ArrayList<Instant>();
    for (int i = 1; i <= 60; i++) days.add(start.plus(i, ChronoUnit.DAYS));
    assertTrue(CaptureCoverage.covers(start, days));
    days.remove(20);
    assertFalse(CaptureCoverage.covers(start, days));
  }

  @Test
  void recommendationsAreSeparateAndDoNotInventModelConfidence() {
    assertNotEquals(
        CustomerFeatures.recommendation(Level.HIGH), CustomerFeatures.recommendation(Level.LOW));
    assertFalse(CustomerFeatures.recommendation(Level.INSUFFICIENT_DATA).contains("%"));
  }

  @Test
  void untrustedArtifactClassesAreRejected() throws Exception {
    var bytes = new java.io.ByteArrayOutputStream();
    try (var out = new java.io.ObjectOutputStream(bytes)) {
      out.writeObject(new java.io.File("untrusted"));
    }
    assertThrows(
        java.io.InvalidClassException.class, () -> DecisionTreeModel.load(bytes.toByteArray()));
  }
}
