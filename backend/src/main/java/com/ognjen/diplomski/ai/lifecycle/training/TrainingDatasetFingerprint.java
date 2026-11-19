package com.ognjen.diplomski.ai.lifecycle.training;

import com.ognjen.diplomski.ai.model.DecisionTreeModel;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;

/** Creates a stable identity for the exact train/evaluation dataset split. */
public final class TrainingDatasetFingerprint {
  private TrainingDatasetFingerprint() {}

  public static String create(DecisionTreeModel.Prepared prepared) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      var training = new HashSet<>(prepared.training());
      for (var example : prepared.selected()) {
        digest.update((byte) (training.contains(example) ? 1 : 2));
        digest.update(ByteBuffer.allocate(8).putLong(example.customerId()).array());
        digest.update(ByteBuffer.allocate(8).putLong(example.at().toEpochMilli()).array());
        digest.update(ByteBuffer.allocate(4).putInt(example.label()).array());
        for (double value : example.features().vector())
          digest.update(ByteBuffer.allocate(8).putLong(Double.doubleToLongBits(value)).array());
      }
      digest.update(ByteBuffer.allocate(8).putLong(prepared.evaluationStart().toEpochMilli()).array());
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception error) {
      throw new IllegalStateException("Could not fingerprint AI training dataset", error);
    }
  }
}
