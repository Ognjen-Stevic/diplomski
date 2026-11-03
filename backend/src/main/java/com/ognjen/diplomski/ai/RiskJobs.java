package com.ognjen.diplomski.ai;

import static com.ognjen.diplomski.ai.RiskTypes.*;

import com.ognjen.diplomski.security.AppUserPrincipal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import smile.classification.DecisionTree;

/**
 * Explicit single-worker administrative lifecycle. Never trains on startup or on prediction reads.
 */
@Service
public class RiskJobs implements AutoCloseable {
  private final RiskStore store;
  private final ObjectProvider<PlatformTransactionManager> transactions;
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "customer-risk-worker");
            t.setDaemon(true);
            return t;
          });
  private final ConcurrentMap<UUID, Map<String, Object>> jobs = new ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicBoolean running =
      new java.util.concurrent.atomic.AtomicBoolean();
  private volatile UUID cachedVersion;
  private volatile DecisionTree cachedTree;

  public RiskJobs(RiskStore store, ObjectProvider<PlatformTransactionManager> transactions) {
    this.store = store;
    this.transactions = transactions;
  }

  public static void admin(AppUserPrincipal user) {
    if (!user.hasRole("CEO"))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CEO can manage risk models");
  }

  public UUID start(AppUserPrincipal user, String operation) {
    admin(user);
    store.db();
    if (!running.compareAndSet(false, true))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "An AI job is already running");
    if (jobs.size() > 20) jobs.clear();
    UUID id = UUID.randomUUID();
    jobs.put(id, Map.of("status", "RUNNING", "operation", operation));
    worker.submit(
        () -> {
          try {
            Object result = operation.equals("train") ? train() : refresh();
            jobs.put(id, Map.of("status", "COMPLETED", "result", result));
          } catch (Exception error) {
            LoggerFactory.getLogger(RiskJobs.class).error("AI job {} failed", id, error);
            jobs.put(
                id,
                Map.of(
                    "status",
                    "FAILED",
                    "message",
                    error instanceof IllegalStateException
                        ? error.getMessage()
                        : "AI job failed; review server logs"));
          } finally {
            running.set(false);
          }
        });
    return id;
  }

  public Map<String, Object> job(AppUserPrincipal user, UUID id) {
    admin(user);
    var job = jobs.get(id);
    if (job == null)
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Job not found (jobs are retained only in this process)");
    return job;
  }

  private static String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private synchronized DecisionTree active() throws Exception {
    var rows =
        store
            .db()
            .queryForList(
                "select version,artifact,sha256 from ai_model where active=true and eligible=true"
                    + " and schema_version=1");
    if (rows.isEmpty()) {
      cachedVersion = null;
      cachedTree = null;
      return null;
    }
    var row = rows.getFirst();
    UUID version = (UUID) row.get("version");
    if (!version.equals(cachedVersion)) {
      byte[] bytes = (byte[]) row.get("artifact");
      if (!hash(bytes).equals(row.get("sha256")))
        throw new IllegalStateException("Model artifact checksum failed");
      cachedTree = DecisionTreeModel.load(bytes);
      cachedVersion = version;
    }
    return cachedTree;
  }

  public List<Map<String, Object>> models(AppUserPrincipal user) {
    admin(user);
    return store
        .db()
        .queryForList(
            "select version,trained_at,schema_version,metrics,eligible,active from ai_model order"
                + " by trained_at desc limit 20");
  }

  public void activate(AppUserPrincipal user, UUID version) {
    admin(user);
    if (running.get())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for the current AI job");
    new TransactionTemplate(transactions.getObject())
        .executeWithoutResult(
            tx -> {
              store.db().execute("lock table ai_model in exclusive mode");
              var rows =
                  store
                      .db()
                      .queryForList(
                          "select artifact,sha256 from ai_model where version=? and eligible=true"
                              + " and schema_version=1",
                          version);
              if (rows.isEmpty())
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Model is absent or did not pass validation");
              try {
                byte[] bytes = (byte[]) rows.getFirst().get("artifact");
                if (!hash(bytes).equals(rows.getFirst().get("sha256")))
                  throw new IllegalStateException("Invalid checksum");
                DecisionTreeModel.load(bytes);
              } catch (Exception error) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Model artifact validation failed", error);
              }
              store.db().update("update ai_model set active=false where active=true");
              store.db().update("update ai_model set active=true where version=?", version);
            });
    cachedVersion = null;
    cachedTree = null;
  }

  private Object train() throws Exception {
    long count = store.db().queryForObject("select count(*) from ai_risk_snapshot", Long.class);
    if (count > 50000)
      throw new IllegalStateException(
          "Snapshot dataset exceeds the 50000-row training limit; archive by an explicit retention"
              + " policy first");
    var captures =
        store
            .db()
            .query(
                "select at from ai_capture_run order by at",
                (r, n) -> r.getTimestamp(1).toInstant());
    long orderCount =
        store.db().queryForObject("select count(*) from ai_order_observation", Long.class);
    if (orderCount > 500000)
      throw new IllegalStateException(
          "Order observations exceed the bounded training budget; configure an audited retention"
              + " policy first");
    var orders =
        store.orders(0, Long.MAX_VALUE).stream()
            .collect(java.util.stream.Collectors.groupingBy(Order::customerId));
    var examples =
        store
            .db()
            .query(
                "select customer_id,at,features from ai_risk_snapshot order by at,customer_id",
                (r, n) -> {
                  var e =
                      new Example(
                          r.getLong(1),
                          r.getTimestamp(2).toInstant(),
                          store.read(r.getString(3), Features.class),
                          0);
                  return new Example(
                      e.customerId(),
                      e.at(),
                      e.features(),
                      CustomerFeatures.label(e, orders.getOrDefault(e.customerId(), List.of())));
                })
            .stream()
            .filter(e -> covered(e.at(), captures))
            .toList();
    var candidate = DecisionTreeModel.train(examples, Instant.now());
    UUID version = UUID.randomUUID();
    byte[] bytes = DecisionTreeModel.save(candidate.tree());
    // Round-trip and prediction identity checked before making any candidate available.
    var restored = DecisionTreeModel.load(bytes);
    for (var e : examples)
      if (DecisionTreeModel.predict(candidate.tree(), e.features())
          != DecisionTreeModel.predict(restored, e.features()))
        throw new IllegalStateException("Artifact round-trip mismatch");
    store
        .db()
        .update(
            "insert into"
                + " ai_model(version,trained_at,schema_version,artifact,sha256,metrics,eligible)"
                + " values (?,now(),1,?,?,?,?)",
            version,
            bytes,
            hash(bytes),
            store.json(candidate.evaluation()),
            candidate.evaluation().eligible());
    return Map.of("version", version, "evaluation", candidate.evaluation());
  }

  private Object refresh() throws Exception {
    DecisionTree model = active();
    UUID version = cachedVersion;
    Instant at = Instant.now();
    // One row per paid, completed requisition; item joins cannot multiply the order count.
    store
        .db()
        .update(
            """
            insert into ai_order_observation(requisition_id,customer_id,created_at,observed_at,liters)
            select r.id,r.customer_id,r.created_at,?,sum(i.quantity*p.volume_liters)::double precision
            from requisition r join requisition_item i on i.requisition_id=r.id join product p on p.id=i.product_id
            where r.status='COMPLETED' and r.type='NORMAL' and r.created_at<=?
            group by r.id,r.customer_id,r.created_at on conflict(requisition_id) do nothing
            """,
            java.sql.Timestamp.from(at),
            java.sql.Timestamp.from(at));
    long last = 0, processed = 0;
    while (true) {
      var customers =
          store
              .db()
              .queryForList(
                  "select id,created_at from customer where id>? order by id limit 500", last);
      if (customers.isEmpty()) break;
      long through = ((Number) customers.getLast().get("id")).longValue();
      var byCustomer =
          store.orders(last, through).stream()
              .collect(java.util.stream.Collectors.groupingBy(Order::customerId));
      new TransactionTemplate(transactions.getObject())
          .executeWithoutResult(
              tx -> {
                for (var customer : customers) {
                  long id = ((Number) customer.get("id")).longValue();
                  Instant since = ((java.sql.Timestamp) customer.get("created_at")).toInstant();
                  Features features =
                      CustomerFeatures.extract(at, since, byCustomer.getOrDefault(id, List.of()));
                  Level risk =
                      !features.sufficient() || model == null
                          ? Level.INSUFFICIENT_DATA
                          : Level.values()[DecisionTreeModel.predict(model, features)];
                  String status =
                      !features.sufficient()
                          ? "INSUFFICIENT_DATA"
                          : model == null ? "MODEL_UNAVAILABLE" : "ASSESSED";
                  var factors =
                      status.equals("ASSESSED")
                          ? DecisionTreeModel.path(model, features)
                          : List.<String>of();
                  store
                      .db()
                      .update(
                          "insert into ai_risk_snapshot(customer_id,at,features) select ?,?,? where"
                              + " not exists(select 1 from ai_risk_snapshot where customer_id=? and"
                              + " at> ?::timestamptz-interval '60 days')",
                          id,
                          java.sql.Timestamp.from(at),
                          store.json(features),
                          id,
                          java.sql.Timestamp.from(at));
                  store
                      .db()
                      .update(
                          """
                          insert into ai_risk_assessment(customer_id,at,level,status,model_version,features,explanation)
                          values (?,?,?,?,?,?,?) on conflict(customer_id) do update set
                          previous_level=case when ai_risk_assessment.model_version=excluded.model_version then ai_risk_assessment.level else null end,
                          at=excluded.at,level=excluded.level,status=excluded.status,model_version=excluded.model_version,
                          features=excluded.features,explanation=excluded.explanation
                          """,
                          id,
                          java.sql.Timestamp.from(at),
                          risk.name(),
                          status,
                          version,
                          store.json(features),
                          store.json(factors));
                  store
                      .db()
                      .update(
                          "insert into ai_risk_history(customer_id,at,level,model_version) values"
                              + " (?,?,?,?)",
                          id,
                          java.sql.Timestamp.from(at),
                          risk.name(),
                          version);
                }
              });
      processed += customers.size();
      last = through;
    }
    store.db().update("insert into ai_capture_run(at) values (?)", java.sql.Timestamp.from(at));
    return Map.of("customers", processed, "at", at, "modelAvailable", model != null);
  }

  static boolean covered(Instant snapshot, List<Instant> captures) {
    Instant end = snapshot.plusSeconds(60L * 86400), previous = snapshot;
    for (Instant capture : captures) {
      if (!capture.isAfter(snapshot) || capture.isAfter(end)) continue;
      if (capture.isAfter(previous.plusSeconds(36 * 3600))) return false;
      previous = capture;
    }
    return !previous.isBefore(end.minusSeconds(24 * 3600));
  }

  @Override
  public void close() {
    worker.shutdown();
  }
}
