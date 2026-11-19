package com.ognjen.diplomski.ai.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import smile.classification.DecisionTree;

/** Serializes only the bounded set of classes required by a Smile decision-tree artifact. */
public final class DecisionTreeArtifactCodec {
  private static final int MAX_ARTIFACT_BYTES = 5_000_000;
  private static final Set<String> ALLOWED_CLASSES =
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

  private DecisionTreeArtifactCodec() {}

  public static byte[] serialize(DecisionTree tree) throws IOException {
    var bytes = new ByteArrayOutputStream();
    try (var output = new ObjectOutputStream(bytes)) {
      output.writeObject(tree);
    }
    return bytes.toByteArray();
  }

  public static DecisionTree deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
    if (bytes.length > MAX_ARTIFACT_BYTES)
      throw new InvalidObjectException("Artifact exceeds size limit");
    try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      input.setObjectInputFilter(DecisionTreeArtifactCodec::filter);
      Object value = input.readObject();
      if (!(value instanceof DecisionTree tree))
        throw new InvalidObjectException("Not a decision tree");
      return tree;
    }
  }

  public static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static ObjectInputFilter.Status filter(ObjectInputFilter.FilterInfo info) {
    if (info.depth() > 100 || info.references() > 100_000 || info.arrayLength() > 100_000)
      return ObjectInputFilter.Status.REJECTED;
    Class<?> type = info.serialClass();
    if (type == null) return ObjectInputFilter.Status.UNDECIDED;
    while (type.isArray()) type = type.getComponentType();
    return type.isPrimitive() || ALLOWED_CLASSES.contains(type.getName())
        ? ObjectInputFilter.Status.ALLOWED
        : ObjectInputFilter.Status.REJECTED;
  }
}
