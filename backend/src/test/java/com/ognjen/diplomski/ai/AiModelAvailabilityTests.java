package com.ognjen.diplomski.ai;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.ognjen.diplomski.ai.lifecycle.model.AiModelService;
import com.ognjen.diplomski.ai.model.DecisionTreeArtifactCodec;
import com.ognjen.diplomski.ai.model.DecisionTreeModel;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.jdbc.core.ResultSetExtractor;
import smile.classification.DecisionTree;

class AiModelAvailabilityTests {
  @Test
  void syntheticProvenanceDoesNotBlockAUsableModel() throws Exception {
    var candidate = DecisionTreeModel.train(examples(), Instant.parse("2026-06-01T00:00:00Z"));
    byte[] artifact = DecisionTreeModel.save(candidate.tree());
    var fixture = fixture("SYNTHETIC_DEMO", artifact, DecisionTreeArtifactCodec.sha256(artifact));

    var availability = fixture.service().availability();

    assertTrue(availability.usable());
    assertEquals("SYNTHETIC_DEMO", availability.active().provenance());
  }

  @Test
  void corruptArtifactIsReportedAsOperationallyUnavailable() {
    var availability = fixture("SYNTHETIC_DEMO", new byte[] {1}, "wrong-checksum").service().availability();

    assertFalse(availability.usable());
    assertTrue(availability.unavailableReason().contains("checksum"));
  }

  @Test
  void absenceOfAnEligibleModelIsANormalUnavailableState() {
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(store.db()).thenReturn(db);
    when(db.queryForList(anyString())).thenReturn(List.of());
    @SuppressWarnings("unchecked")
    ObjectProvider<PlatformTransactionManager> transactions = mock(ObjectProvider.class);

    var availability = new AiModelService(store, transactions).availability();

    assertFalse(availability.usable());
    assertNull(availability.registeredVersion());
  }

  @Test
  void promotionClosesIncumbentActivationAndPersistsNewActivationHistory() throws Exception {
    var candidate = DecisionTreeModel.train(examples(), Instant.parse("2026-06-01T00:00:00Z"));
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(store.db()).thenReturn(db);
    @SuppressWarnings("unchecked")
    ObjectProvider<PlatformTransactionManager> transactions = mock(ObjectProvider.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    TransactionStatus transaction = mock(TransactionStatus.class);
    when(transactions.getObject()).thenReturn(transactionManager);
    when(transactionManager.getTransaction(any())).thenReturn(transaction);
    UUID incumbentId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    DecisionTree weakIncumbent = mock(DecisionTree.class);
    when(weakIncumbent.schema()).thenReturn(candidate.tree().schema());
    when(weakIncumbent.predict(any(smile.data.Tuple.class))).thenReturn(0);
    var service = spy(new AiModelService(store, transactions));
    doReturn(
            new AiModelService.Availability(
                incumbentId,
                "OPERATIONAL",
                new AiModelService.Active(incumbentId, "V003", weakIncumbent, "OPERATIONAL"),
                null))
        .when(service)
        .availability();
    when(db.query(eq("select version from ai_model where active=true"), any(ResultSetExtractor.class)))
        .thenReturn(incumbentId);
    when(db.queryForObject(
            eq("select model_version from ai_model where version=?"),
            eq(String.class),
            eq(candidateId)))
        .thenReturn("V004");
    when(db.update(contains("update ai_model set active=true"), any(Object[].class))).thenReturn(1);

    var promotion =
        service.promote(
            candidateId,
            candidate.tree(),
            candidate.evaluation(),
            candidate.evaluation().tree().count() > 0
                ? DecisionTreeRiskTests.fixture().subList(300, 450)
                : List.of(),
            "OPERATIONAL");

    assertTrue(promotion.promoted());
    assertNotNull(promotion.incumbentMetrics());
    verify(db).update(contains("update ai_model_activation set deactivated_at"), eq(candidateId), eq(incumbentId));
    verify(db)
        .update(
            contains("insert into ai_model_activation"),
            eq(incumbentId),
            eq(candidateId));
  }

  private static Fixture fixture(String provenance, byte[] artifact, String sha256) {
    RiskStore store = mock(RiskStore.class);
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(store.db()).thenReturn(db);
    Map<String, Object> row = new HashMap<>();
    row.put("version", UUID.randomUUID());
    row.put("provenance", provenance);
    row.put("artifact", artifact);
    row.put("sha256", sha256);
    when(db.queryForList(anyString())).thenReturn(List.of(row));
    @SuppressWarnings("unchecked")
    ObjectProvider<PlatformTransactionManager> transactions = mock(ObjectProvider.class);
    return new Fixture(store, transactions);
  }

  private static List<Example> examples() {
    var result = new ArrayList<Example>();
    Instant start = Instant.parse("2025-01-01T00:00:00Z");
    for (int period = 0; period < 3; period++)
      for (int i = 0; i < 150; i++) {
        int label = i % 3;
        var features = new Features(5, 7, 5, 10, 15, 100 + label * 100, 100, 400);
        result.add(
            new Example(i + 1, start.plus(period * 120L, ChronoUnit.DAYS), features, label));
      }
    return result;
  }

  private record Fixture(
      RiskStore store, ObjectProvider<PlatformTransactionManager> transactions) {
    AiModelService service() {
      return new AiModelService(store, transactions);
    }
  }
}
