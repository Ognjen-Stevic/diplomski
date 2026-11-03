package com.ognjen.diplomski.ai;

import static com.ognjen.diplomski.ai.RiskTypes.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Identical feature construction for capture/training and live assessments. No backdated snapshots.
 */
public final class CustomerFeatures {
  private CustomerFeatures() {}

  public static Features extract(Instant at, Instant customerSince, List<Order> orders) {
    var known =
        orders.stream()
            .filter(o -> !o.createdAt().isAfter(at) && !o.observedAt().isAfter(at))
            .sorted(Comparator.comparing(Order::createdAt))
            .toList();
    var recent =
        known.stream().filter(o -> o.createdAt().isAfter(at.minus(90, ChronoUnit.DAYS))).toList();
    double gap =
        recent.size() < 2
            ? 0
            : days(recent.getFirst().createdAt(), recent.getLast().createdAt())
                / (recent.size() - 1);
    return new Features(
        known.isEmpty() ? days(customerSince, at) : days(known.getLast().createdAt(), at),
        gap,
        count(known, at, 30),
        count(known, at, 60),
        recent.size(),
        volume(known, at, 60),
        volume(known, at.minus(60, ChronoUnit.DAYS), 60),
        days(customerSince, at));
  }

  private static double days(Instant a, Instant b) {
    return Math.max(0, (b.toEpochMilli() - a.toEpochMilli()) / 86400000.0);
  }

  private static long count(List<Order> orders, Instant end, int days) {
    return orders.stream().filter(o -> in(o, end, days)).count();
  }

  public static double volume(List<Order> orders, Instant end, int days) {
    return orders.stream().filter(o -> in(o, end, days)).mapToDouble(Order::liters).sum();
  }

  private static boolean in(Order o, Instant end, int days) {
    return o.createdAt().isAfter(end.minus(days, ChronoUnit.DAYS))
        && !o.createdAt().isAfter(end)
        && !o.observedAt().isAfter(end);
  }

  public static int label(Example snapshot, List<Order> orders) {
    Instant end = snapshot.at().plus(60, ChronoUnit.DAYS);
    var future = orders.stream().filter(o -> in(o, end, 60)).toList();
    return future.isEmpty()
        ? 2
        : future.stream().mapToDouble(Order::liters).sum() < snapshot.features().liters60() * 0.5
            ? 1
            : 0;
  }

  public static String recommendation(Level level) {
    return switch (level) {
      case HIGH -> "Kontaktirajte kupca i proverite razlog pada aktivnosti.";
      case MEDIUM -> "Pratite poručivanje i razmotrite preventivni kontakt.";
      case LOW -> "Nastavite redovno praćenje kupca.";
      case INSUFFICIENT_DATA -> "Procena nije dostupna; prikupite istoriju poručivanja.";
    };
  }
}
