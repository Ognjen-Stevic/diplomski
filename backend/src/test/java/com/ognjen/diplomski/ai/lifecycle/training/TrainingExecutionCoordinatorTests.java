package com.ognjen.diplomski.ai.lifecycle.training;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ognjen.diplomski.ai.persistence.RiskStore;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TrainingExecutionCoordinatorTests {
  @Test
  void weeklyScheduleKeyCanStartOnlyOnceAcrossConcurrentOrDstInvocations() {
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(store.db()).thenReturn(db);
    when(db.update(contains("on conflict do nothing"), any(Object[].class))).thenReturn(1, 0);
    var coordinator = new TrainingExecutionCoordinator(store, Duration.ofHours(1));

    var first = coordinator.startOnce("ELIGIBILITY", null, "WEEKLY_TRAINING:2026-10-25");
    var duplicate = coordinator.startOnce("ELIGIBILITY", null, "WEEKLY_TRAINING:2026-10-25");

    assertNotNull(first);
    assertNull(duplicate);
  }
}
