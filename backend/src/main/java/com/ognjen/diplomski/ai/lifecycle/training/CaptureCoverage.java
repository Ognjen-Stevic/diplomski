package com.ognjen.diplomski.ai.lifecycle.training;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Verifies that capture runs continuously cover a snapshot's outcome window. */
public final class CaptureCoverage {
  private CaptureCoverage() {}

  public static boolean covers(Instant snapshot, List<Instant> captures) {
    Instant end = snapshot.plus(60, ChronoUnit.DAYS), previous = snapshot;
    for (Instant capture : captures) {
      if (!capture.isAfter(snapshot) || capture.isAfter(end)) continue;
      if (capture.isAfter(previous.plus(36, ChronoUnit.HOURS))) return false;
      previous = capture;
    }
    return !previous.isBefore(end.minus(24, ChronoUnit.HOURS));
  }
}
