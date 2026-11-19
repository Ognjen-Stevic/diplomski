package com.ognjen.diplomski.ai.lifecycle.scheduling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ognjen.diplomski.ai.lifecycle.capture.AiCaptureService;
import com.ognjen.diplomski.ai.lifecycle.training.AiTrainingService;
import com.ognjen.diplomski.ai.service.AiAssessmentService;
import com.ognjen.diplomski.ai.service.AiOperationalStatusService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class AiLifecycleSchedulerTests {
  @Test
  void captureKeepsSixtySecondFixedDelayContract() throws Exception {
    Scheduled scheduled = annotation("captureAndReconcile");

    assertEquals("${ai.lifecycle.capture-delay-ms:60000}", scheduled.fixedDelayString());
    assertEquals("${ai.lifecycle.capture-delay-ms:60000}", scheduled.initialDelayString());
  }

  @Test
  void dailyAssessmentRunsAt0215InBelgrade() throws Exception {
    Scheduled scheduled = annotation("maintainDailySnapshots");

    assertEquals("${app.ai.assessment.cron:0 15 2 * * *}", scheduled.cron());
    assertEquals("${app.ai.time-zone:Europe/Belgrade}", scheduled.zone());
  }

  @Test
  void trainingEligibilityRunsOnlySundayAt0300InBelgrade() throws Exception {
    Scheduled scheduled = annotation("checkTrainingEligibility");

    assertEquals("${app.ai.training.cron:0 0 3 * * SUN}", scheduled.cron());
    assertEquals("${app.ai.time-zone:Europe/Belgrade}", scheduled.zone());
    assertTrue(scheduled.fixedDelayString().isEmpty());
  }

  @Test
  void startupRecoversWorkAndRecordsMissedSlotWithoutStartingTraining() {
    AiCaptureService capture = mock(AiCaptureService.class);
    AiAssessmentService assessments = mock(AiAssessmentService.class);
    AiTrainingService training = mock(AiTrainingService.class);
    AiOperationalStatusService health = mock(AiOperationalStatusService.class);
    var scheduler =
        new AiLifecycleScheduler(
            capture, assessments, training, health, "Europe/Belgrade");

    scheduler.recover();

    verify(training).recoverAbandonedWork();
    verify(capture).recoverExpiredEvents();
    verify(capture).reconcileAndProcess();
    verify(training).recordMissedSchedule(startsWith("WEEKLY_TRAINING:"), any());
    verify(training, never()).checkAndTrain(anyString());
  }

  private static Scheduled annotation(String methodName) throws Exception {
    Method method = AiLifecycleScheduler.class.getDeclaredMethod(methodName);
    return method.getAnnotation(Scheduled.class);
  }
}
