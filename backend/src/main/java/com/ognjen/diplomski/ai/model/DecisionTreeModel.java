package com.ognjen.diplomski.ai.model;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import smile.base.cart.InternalNode;
import smile.base.cart.DecisionNode;
import smile.base.cart.OrdinalNode;
import smile.base.cart.SplitRule;
import smile.classification.DecisionTree;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.vector.IntVector;

/** Smile CART, bounded complexity; no risk thresholds are disguised as a learned classifier. */
public final class DecisionTreeModel {
  private DecisionTreeModel() {}

  public record Candidate(DecisionTree tree, Evaluation evaluation) {}

  public record TraversalStep(
      String feature, double value, double threshold, String operator, String selectedBranch) {}

  public record PredictionTrace(int prediction, List<TraversalStep> traversal) {}

  public record Prepared(
      List<Example> selected,
      List<Example> training,
      List<Example> evaluation,
      Instant evaluationStart) {}

  public static Candidate train(List<Example> input, Instant now) {
    return train(prepare(input, now), now);
  }

  public static Prepared prepare(List<Example> input, Instant now) {
    var selected = new ArrayList<Example>();
    var previous = new HashMap<Long, Instant>();
    input.stream()
        .filter(e -> e.features().sufficient() && !e.at().plus(60, ChronoUnit.DAYS).isAfter(now))
        .sorted(Comparator.comparing(Example::at))
        .forEach(
            e -> {
              Instant last = previous.get(e.customerId());
              if (last == null || !e.at().isBefore(last.plus(60, ChronoUnit.DAYS))) {
                selected.add(e);
                previous.put(e.customerId(), e.at());
              }
            });
    if (selected.size() < 150)
      throw new IllegalStateException(
          "At least 150 mature, non-overlapping snapshots are required");
    Instant cutoff = selected.get((int) (selected.size() * 0.7)).at();
    // Purge the complete outcome window from the training/evaluation boundary.
    var train =
        selected.stream().filter(e -> e.at().plus(60, ChronoUnit.DAYS).isBefore(cutoff)).toList();
    var test = selected.stream().filter(e -> !e.at().isBefore(cutoff)).toList();
    if (train.size() < 90 || test.size() < 30 || !represented(train) || !represented(test))
      throw new IllegalStateException(
          "Insufficient chronological coverage: require 90 train / 30 evaluation and 10 of each"
              + " class in both");
    return new Prepared(List.copyOf(selected), train, test, cutoff);
  }

  public static Candidate train(Prepared prepared, Instant now) {
    var train = prepared.training();
    var test = prepared.evaluation();
    double[][] x = train.stream().map(e -> e.features().vector()).toArray(double[][]::new);
    int[] y = train.stream().mapToInt(Example::label).toArray();
    var frame = DataFrame.of(x, Features.NAMES).add(new IntVector("risk", y));
    var tree =
        DecisionTree.fit(
            Formula.lhs("risk"), frame, new DecisionTree.Options(SplitRule.GINI, 5, 16, 10));
    Metrics model = evaluate(test, e -> predict(tree, e.features()));
    Metrics baseline =
        evaluate(
            test,
            e -> e.features().recencyDays() > 60 ? 2 : e.features().recencyDays() > 30 ? 1 : 0);
    boolean eligible =
        model.macroF1() > baseline.macroF1() + 0.02
            && Arrays.stream(model.recall()).allMatch(v -> v >= 0.4);
    return new Candidate(
        tree,
        new Evaluation(
            train.size(),
            model,
            baseline,
            eligible,
            eligible
                ? "Validation gates passed; eligible for autonomous promotion review"
                : "Rejected: must exceed baseline macro-F1 by 0.02 and recall >= 0.4 for every"
                    + " class",
            train.stream()
                .map(e -> e.at().plus(60, ChronoUnit.DAYS))
                .max(Instant::compareTo)
                .orElseThrow(),
            prepared.evaluationStart(),
            now,
            "Smile 4.4.0 CART / GINI"));
  }

  private static boolean represented(List<Example> data) {
    return java.util.stream.IntStream.range(0, 3)
        .allMatch(c -> data.stream().filter(e -> e.label() == c).count() >= 10);
  }

  public static int predict(DecisionTree tree, Features features) {
    return tree.predict(Tuple.of(tree.schema(), features.vector()));
  }

  public static List<String> path(DecisionTree tree, Features features) {
    var tuple = Tuple.of(tree.schema(), features.vector());
    var node = tree.root();
    var path = new ArrayList<String>();
    while (node instanceof InternalNode branch) {
      boolean yes = branch.branch(tuple);
      path.add(branch.toString(tree.schema(), yes));
      node = yes ? branch.trueChild() : branch.falseChild();
    }
    return List.copyOf(path);
  }

  /** Exact numeric CART traversal, checked against Smile's native prediction. */
  public static PredictionTrace trace(DecisionTree tree, Features features) {
    var tuple = Tuple.of(tree.schema(), features.vector());
    var node = tree.root();
    var traversal = new ArrayList<TraversalStep>();
    while (node instanceof InternalNode branch) {
      if (!(branch instanceof OrdinalNode ordinal))
        throw new IllegalStateException("Unsupported non-numeric Decision Tree split");
      boolean selected = ordinal.branch(tuple);
      int feature = ordinal.feature();
      double threshold = ordinalThreshold(ordinal);
      traversal.add(
          new TraversalStep(
              Features.NAMES[feature],
              features.vector()[feature],
              threshold,
              selected ? "<=" : ">",
              selected ? "TRUE" : "FALSE"));
      node = selected ? ordinal.trueChild() : ordinal.falseChild();
    }
    if (!(node instanceof DecisionNode leaf))
      throw new IllegalStateException("Decision Tree traversal did not terminate in a decision leaf");
    int nativePrediction = predict(tree, features);
    if (leaf.output() != nativePrediction)
      throw new IllegalStateException("Custom traversal disagrees with Smile native prediction");
    return new PredictionTrace(nativePrediction, List.copyOf(traversal));
  }

  private static double ordinalThreshold(OrdinalNode node) {
    try {
      var field = OrdinalNode.class.getDeclaredField("value");
      field.setAccessible(true);
      return field.getDouble(node);
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("Cannot read Smile Decision Tree threshold", error);
    }
  }

  public static Metrics evaluate(
      List<Example> data, java.util.function.ToIntFunction<Example> prediction) {
    int[][] matrix = new int[3][3];
    int[] distribution = new int[3];
    for (var e : data) {
      matrix[e.label()][prediction.applyAsInt(e)]++;
      distribution[e.label()]++;
    }
    double[] precision = new double[3], recall = new double[3], f1 = new double[3];
    for (int c = 0; c < 3; c++) {
      int predicted = 0;
      for (int r = 0; r < 3; r++) predicted += matrix[r][c];
      precision[c] = predicted == 0 ? 0 : (double) matrix[c][c] / predicted;
      recall[c] = distribution[c] == 0 ? 0 : (double) matrix[c][c] / distribution[c];
      f1[c] =
          precision[c] + recall[c] == 0
              ? 0
              : 2 * precision[c] * recall[c] / (precision[c] + recall[c]);
    }
    return new Metrics(
        data.size(),
        matrix,
        precision,
        recall,
        f1,
        Arrays.stream(f1).average().orElse(0),
        distribution);
  }

  public static byte[] save(DecisionTree tree) throws IOException {
    return DecisionTreeArtifactCodec.serialize(tree);
  }

  public static DecisionTree load(byte[] bytes) throws IOException, ClassNotFoundException {
    return DecisionTreeArtifactCodec.deserialize(bytes);
  }
}
