package com.ognjen.diplomski.ai.audit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ognjen.diplomski.ai.lifecycle.model.AiModelService;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import com.ognjen.diplomski.ai.service.AiAssessmentService;
import com.ognjen.diplomski.ai.service.AiOperationalStatusService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class AiAssessmentAuditTests {
  @Test
  void persistenceRollbackCannotProduceFalseAssessmentSuccessAudit() {
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    AiModelService models = mock(AiModelService.class);
    AiOperationalStatusService health = mock(AiOperationalStatusService.class);
    AiAuditLogger audit = mock(AiAuditLogger.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    TransactionStatus transaction = mock(TransactionStatus.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<PlatformTransactionManager> provider = mock(ObjectProvider.class);
    when(store.db()).thenReturn(db);
    when(provider.getObject()).thenReturn(transactions);
    when(transactions.getTransaction(any())).thenReturn(transaction);
    doThrow(new IllegalStateException("commit failed")).when(transactions).commit(transaction);
    when(db.queryForList(contains("select c.id,c.created_at"), eq(42L)))
        .thenReturn(List.of(Map.of("id", 42L, "created_at", Timestamp.from(Instant.parse("2025-01-01T00:00:00Z")))));
    when(models.availability())
        .thenReturn(new AiModelService.Availability(null, null, null, "unavailable"));
    when(store.orders(anyList())).thenReturn(List.of());
    var service = new AiAssessmentService(store, models, provider, health, audit, 3);

    assertThrows(
        RuntimeException.class,
        () -> service.refreshCustomer(42L, Instant.parse("2026-01-01T00:00:00Z"), false));

    verify(audit, never()).decision(anyString(), any(), any(), anyMap());
  }
}
