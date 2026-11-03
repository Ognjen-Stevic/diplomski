package com.ognjen.diplomski.ai;

import static com.ognjen.diplomski.ai.RiskTypes.*;

import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import smile.base.cart.InternalNode;
import smile.base.cart.SplitRule;
import smile.classification.DecisionTree;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.vector.IntVector;

/** Smile CART, bounded complexity; no risk thresholds are disguised as a learned classifier. */
public final class DecisionTreeModel {
  private static final Set<String> ARTIFACT_CLASSES =
      Set.of(
          "smile.classification.DecisionTree",
          "smile.base.cart.CART",
          "smile.base.cart.OrdinalNode",
          "smile.base.cart.InternalNode",
          "smile.base.cart.DecisionNode",
          "smile.base.cart.LeafNode",
          "smile.base.cart.SplitRule",
          "smile.data.formula.Formula",
          "smile.data.formula.Term",
          "smile.data.formula.Variable",
          "smile.data.type.StructField",
          "smile.data.type.IntType",
          "smile.data.type.PrimitiveType",
          "smile.data.type.DataType$ID",
          "smile.data.type.StructType",
          "smile.data.type.DoubleType",
          "smile.util.IntSet",
          "java.lang.Enum",
          "java.lang.Object",
          "java.lang.Integer",
          "java.lang.Number",
          "java.util.ArrayList",
          "java.util.HashMap",
          "java.util.Map$Entry");

  private DecisionTreeModel() {}

  public record Candidate(DecisionTree tree, Evaluation evaluation) {}

  public static Candidate train(List<Example> input, Instant now) {
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
                ? "Validation gates passed; explicit CEO activation required"
                : "Rejected: must exceed baseline macro-F1 by 0.02 and recall >= 0.4 for every"
                    + " class",
            train.stream()
                .map(e -> e.at().plus(60, ChronoUnit.DAYS))
                .max(Instant::compareTo)
                .orElseThrow(),
            cutoff,
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
    var bytes = new ByteArrayOutputStream();
    try (var out = new ObjectOutputStream(bytes)) {
      out.writeObject(tree);
    }
    return bytes.toByteArray();
  }

  public static DecisionTree load(byte[] bytes) throws IOException, ClassNotFoundException {
    if (bytes.length > 5_000_000) throw new InvalidObjectException("Artifact exceeds size limit");
    try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      in.setObjectInputFilter(
          info -> {
            if (info.depth() > 100 || info.references() > 100_000 || info.arrayLength() > 100_000)
              return ObjectInputFilter.Status.REJECTED;
            Class<?> type = info.serialClass();
            if (type == null) return ObjectInputFilter.Status.UNDECIDED;
            while (type.isArray()) type = type.getComponentType();
            String n = type.getName();
            return type.isPrimitive() || ARTIFACT_CLASSES.contains(n)
                ? ObjectInputFilter.Status.ALLOWED
                : ObjectInputFilter.Status.REJECTED;
          });
      Object value = in.readObject();
      if (!(value instanceof DecisionTree tree))
        throw new InvalidObjectException("Not a decision tree");
      return tree;
    }
  }
}
