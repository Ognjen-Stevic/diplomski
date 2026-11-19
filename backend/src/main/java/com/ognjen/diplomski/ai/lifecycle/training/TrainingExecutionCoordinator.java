package com.ognjen.diplomski.ai.lifecycle.training;

import static com.ognjen.diplomski.ai.support.AiErrorMessages.safe;

import com.ognjen.diplomski.ai.persistence.RiskStore;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Owns durable job bookkeeping and the cross-process training lease. */
@Component
class TrainingExecutionCoordinator {
  private final RiskStore store;
  private final Duration lease;

  TrainingExecutionCoordinator(
      RiskStore store, @Value("${ai.lifecycle.lock-lease:PT1H}") Duration lease) {
    this.store = store;
    this.lease = lease;
  }

  boolean acquire(UUID owner) {
    return store
            .db()
            .update(
                "update ai_lifecycle_lock set owner_id=?,lease_until=now()+make_interval(secs=>?),"
                    + "updated_at=now() where lock_name='TRAINING' and"
                    + " (owner_id is null or lease_until<now())",
                owner,
                Math.toIntExact(lease.toSeconds()))
        == 1;
  }

  void release(UUID owner) {
    store
        .db()
        .update(
            "update ai_lifecycle_lock set owner_id=null,lease_until=null,updated_at=now()"
                + " where lock_name='TRAINING' and owner_id=?",
            owner);
  }

  void recoverAbandonedWork() {
    store
        .db()
        .update(
            "update ai_job_execution set status='ABANDONED',completed_at=now(),"
                + "outcome='Prekinut rad je otkriven nakon ponovnog pokretanja.'"
                + " where status='RUNNING' and started_at<now()-make_interval(secs=>?)",
            Math.toIntExact(lease.toSeconds()));
    store
        .db()
        .update(
            "update ai_lifecycle_lock set owner_id=null,lease_until=null,updated_at=now()"
                + " where lease_until<now()");
  }

  UUID start(String type, String fingerprint) {
    UUID id = UUID.randomUUID();
    store
        .db()
        .update(
            "insert into ai_job_execution(id,job_type,status,dataset_fingerprint,owner_id)"
                + " values (?,?,'RUNNING',?,?)",
            id,
            type,
            fingerprint,
            id);
    return id;
  }

  UUID startOnce(String type, String fingerprint, String scheduleKey) {
    UUID id = UUID.randomUUID();
    int inserted =
        store
            .db()
            .update(
                "insert into ai_job_execution(id,job_type,status,dataset_fingerprint,owner_id,schedule_key)"
                    + " values (?,?,'RUNNING',?,?,?) on conflict do nothing",
                id,
                type,
                fingerprint,
                id,
                scheduleKey);
    return inserted == 1 ? id : null;
  }

  boolean recordMissed(String type, String scheduleKey, java.time.Instant scheduledAt) {
    UUID id = UUID.randomUUID();
    int inserted = store
        .db()
        .update(
            "insert into ai_job_execution(id,job_type,status,schedule_key,started_at,completed_at,"
                + "outcome) values (?,?, 'MISSED',?,?,now(),?) on conflict do nothing",
            id,
            type,
            scheduleKey,
            java.sql.Timestamp.from(scheduledAt),
            "Zakazani nedeljni termin je propuÅ¡ten dok aplikacija nije radila.");
    return inserted == 1;
  }

  void finish(UUID id, String status, String outcome, String details, String error) {
    store
        .db()
        .update(
            "update ai_job_execution set status=?,completed_at=now(),outcome=?,details=?,"
                + "error_message=? where id=?",
            status,
            outcome,
            details,
            error,
            id);
  }

  int attempts(String fingerprint) {
    if (fingerprint == null) return 0;
    Integer value =
        store
            .db()
            .queryForObject(
                "select attempts from ai_training_dataset where fingerprint=?",
                Integer.class,
                fingerprint);
    return value == null ? 0 : value;
  }

  void recordPersistentError(Exception error) {
    store
        .db()
        .update(
            "update ai_system_state set operational_status='DEGRADED',persistent_error=?,"
                + "last_training_attempt_at=now(),last_training_outcome='FAILED',updated_at=now()"
                + " where singleton",
            safe(error));
  }
}
