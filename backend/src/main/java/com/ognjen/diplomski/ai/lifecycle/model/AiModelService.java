package com.ognjen.diplomski.ai.lifecycle.model;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;

import com.ognjen.diplomski.ai.model.DecisionTreeArtifactCodec;
import com.ognjen.diplomski.ai.model.DecisionTreeModel;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import smile.classification.DecisionTree;

/** Trusted model loading, read-only registry access and transactional autonomous promotion. */
@Service
public class AiModelService {
  public record Active(UUID version, String modelVersion, DecisionTree tree, String provenance) {}

  public record Availability(
      UUID registeredVersion, String provenance, Active active, String unavailableReason) {
    public boolean usable() {
      return active != null;
    }
  }

  public record Promotion(
      boolean promoted,
      String decision,
      String reason,
      UUID previousModel,
      Metrics incumbentMetrics) {}

  private final RiskStore store;
  private final ObjectProvider<PlatformTransactionManager> transactionManagers;
  private volatile Active cached;

  public AiModelService(
      RiskStore store,
      ObjectProvider<PlatformTransactionManager> transactionManagers) {
    this.store = store;
    this.transactionManagers = transactionManagers;
  }

  public synchronized Active active() throws Exception {
    return availability().active();
  }

  public synchronized Availability availability() {
    var rows =
        store
            .db()
            .queryForList(
                "select version,model_version,artifact,sha256,provenance from ai_model where active=true"
                    + " and eligible=true and schema_version=1");
    if (rows.isEmpty()) {
      cached = null;
      return new Availability(null, null, null, "No eligible active model is available.");
    }
    var row = rows.getFirst();
    UUID version = (UUID) row.get("version");
    String modelVersion = (String) row.get("model_version");
    String provenance = (String) row.get("provenance");
    if (cached != null && cached.version().equals(version))
      return new Availability(version, provenance, cached, null);
    try {
      byte[] artifact = (byte[]) row.get("artifact");
      if (!DecisionTreeArtifactCodec.sha256(artifact).equals(row.get("sha256")))
        return new Availability(version, provenance, null, "Model artifact checksum failed.");
      cached = new Active(version, modelVersion, DecisionTreeModel.load(artifact), provenance);
      return new Availability(version, provenance, cached, null);
    } catch (Exception error) {
      cached = null;
      String message = error.getMessage();
      return new Availability(
          version,
          provenance,
          null,
          "Model artifact could not be loaded: "
              + (message == null || message.isBlank()
                  ? error.getClass().getSimpleName()
                  : message));
    }
  }

  public Promotion promote(
      UUID candidateVersion,
      DecisionTree candidate,
      Evaluation candidateEvaluation,
      List<Example> commonEvaluation,
      String provenance)
      throws Exception {
    if (!candidateEvaluation.eligible())
      return decision(
          candidateVersion,
          false,
          "REJECTED",
          "Novi model nije prošao postojeće validacione uslove.",
          null,
          null);

    Availability incumbent = availability();
    Active previous = incumbent.active();
    Metrics incumbentMetrics = null;
    if (previous != null) {
      Metrics comparison =
          DecisionTreeModel.evaluate(
              commonEvaluation, e -> DecisionTreeModel.predict(previous.tree(), e.features()));
      incumbentMetrics = comparison;
      boolean meaningful =
          candidateEvaluation.tree().macroF1() > comparison.macroF1() + 0.02;
      boolean noClassRegression =
          java.util.stream.IntStream.range(0, 3)
              .allMatch(
                  i ->
                      candidateEvaluation.tree().recall()[i]
                          >= comparison.recall()[i] - 0.05);
      if (!meaningful || !noClassRegression)
        return decision(
            candidateVersion,
            false,
            "KEPT_ACTIVE_MODEL",
            "Novi model nema dokazano poboljšanje na istom evaluacionom skupu ili pogoršava"
                + " odziv neke klase.",
            previous.version(),
            incumbentMetrics);
    }

    UUID expectedPrevious = incumbent.registeredVersion();
    Boolean promoted =
        new TransactionTemplate(transactionManagers.getObject()).execute(
            status -> {
              store.db().execute("lock table ai_model in exclusive mode");
              UUID current =
                  store
                      .db()
                      .query(
                          "select version from ai_model where active=true",
                          r -> r.next() ? (UUID) r.getObject(1) : null);
              if (!Objects.equals(current, expectedPrevious)) return false;
              String candidateModelVersion =
                  store
                      .db()
                      .queryForObject(
                          "select model_version from ai_model where version=?",
                          String.class,
                          candidateVersion);
              if (current != null)
                store
                    .db()
                    .update(
                        "update ai_model_activation set deactivated_at=now(),replaced_by_model_id=?"
                            + " where model_id=? and deactivated_at is null",
                        candidateVersion,
                        current);
              store.db().update("update ai_model set active=false where active=true");
              boolean activated = store
                      .db()
                      .update(
                          "update ai_model set active=true,promotion_decision='PROMOTED',"
                              + "promotion_reason=?,promoted_at=now(),compared_to_model=?"
                              + " where version=? and eligible=true and provenance=?",
                          previous == null
                              ? "Prvi upotrebljivi validirani model je automatski aktiviran."
                              : "Novi model je bolji na zajedničkom evaluacionom skupu.",
                          expectedPrevious,
                          candidateVersion,
                          provenance)
                  == 1;
              if (activated)
                store
                    .db()
                    .update(
                        "insert into ai_model_activation(model_id,model_version,replaced_model_id,reason)"
                            + " select version,model_version,?,promotion_reason from ai_model where version=?",
                        expectedPrevious,
                        candidateVersion);
              return activated;
            });
    if (!Boolean.TRUE.equals(promoted))
      return decision(
          candidateVersion,
          false,
          "CONCURRENT_CHANGE",
          "Aktivni model se promenio tokom evaluacije; kandidat nije promovisan.",
          expectedPrevious,
          incumbentMetrics);
    cached = null;
    return new Promotion(
        true,
        "PROMOTED",
        previous == null
            ? "Prvi upotrebljivi validirani model je automatski aktiviran."
            : "Novi model je prošao evaluaciju i automatski je aktiviran.",
        expectedPrevious,
        incumbentMetrics);
  }

  private Promotion decision(
      UUID candidate,
      boolean promoted,
      String value,
      String reason,
      UUID previous,
      Metrics incumbentMetrics) {
    store
        .db()
        .update(
            "update ai_model set promotion_decision=?,promotion_reason=?,compared_to_model=?"
                + " where version=?",
            value,
            reason,
            previous,
            candidate);
    return new Promotion(promoted, value, reason, previous, incumbentMetrics);
  }

}
