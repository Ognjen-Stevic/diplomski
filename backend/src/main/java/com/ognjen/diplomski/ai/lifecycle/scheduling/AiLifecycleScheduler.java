package com.ognjen.diplomski.ai.lifecycle.scheduling;

import com.ognjen.diplomski.ai.lifecycle.capture.AiCaptureService;
import com.ognjen.diplomski.ai.lifecycle.training.AiTrainingService;
import com.ognjen.diplomski.ai.service.AiAssessmentService;
import com.ognjen.diplomski.ai.service.AiOperationalStatusService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

/** Configurable internal scheduling; every operation is idempotent and database-coordinated. */
@Component
@ConditionalOnProperty(
    name = "ai.lifecycle.scheduling.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AiLifecycleScheduler {
  private final AiCaptureService capture;
  private final AiAssessmentService assessments;
  private final AiTrainingService training;
  private final AiOperationalStatusService health;
  private final ZoneId timeZone;

  public AiLifecycleScheduler(
      AiCaptureService capture,
      AiAssessmentService assessments,
      AiTrainingService training,
      AiOperationalStatusService health,
      @Value("${app.ai.time-zone:Europe/Belgrade}") String timeZone) {
    this.capture = capture;
    this.assessments = assessments;
    this.training = training;
    this.health = health;
    this.timeZone = ZoneId.of(timeZone);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    try {
      health.heartbeat();
      training.recoverAbandonedWork();
      capture.recoverExpiredEvents();
      capture.reconcileAndProcess();
      ZonedDateTime missed = mostRecentWeeklySlot(Instant.now());
      training.recordMissedSchedule(scheduleKey(missed), missed.toInstant());
    } catch (Exception error) {
      LoggerFactory.getLogger(AiLifecycleScheduler.class)
          .error("AI startup recovery will be retried by scheduled maintenance", error);
    }
  }

  @Scheduled(
      fixedDelayString = "${ai.lifecycle.capture-delay-ms:60000}",
      initialDelayString = "${ai.lifecycle.capture-delay-ms:60000}")
  public void captureAndReconcile() {
    health.heartbeat();
    capture.reconcileAndProcess();
  }

  @Scheduled(
      cron = "${app.ai.training.cron:0 0 3 * * SUN}",
      zone = "${app.ai.time-zone:Europe/Belgrade}")
  public void checkTrainingEligibility() {
    health.heartbeat();
    training.checkAndTrain(scheduleKey(Instant.now().atZone(timeZone)));
  }

  @Scheduled(
      cron = "${app.ai.assessment.cron:0 15 2 * * *}",
      zone = "${app.ai.time-zone:Europe/Belgrade}")
  public void maintainDailySnapshots() {
    try {
      health.heartbeat();
      assessments.maintainDailySnapshots(Instant.now());
    } catch (Exception error) {
      LoggerFactory.getLogger(AiLifecycleScheduler.class)
          .error("Daily AI snapshot maintenance failed", error);
    }
  }

  private ZonedDateTime mostRecentWeeklySlot(Instant now) {
    ZonedDateTime local = now.atZone(timeZone);
    ZonedDateTime slot =
        local
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .withHour(3)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
    return slot.isAfter(local) ? slot.minusWeeks(1) : slot;
  }

  private static String scheduleKey(ZonedDateTime slot) {
    return "WEEKLY_TRAINING:" + slot.toLocalDate();
  }
}
