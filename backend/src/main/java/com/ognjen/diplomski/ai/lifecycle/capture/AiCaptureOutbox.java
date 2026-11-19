package com.ognjen.diplomski.ai.lifecycle.capture;

import com.ognjen.diplomski.ai.persistence.RiskStore;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Writes a durable completion event after the business transaction commits. */
@Component
public class AiCaptureOutbox {
  private final RiskStore store;

  public AiCaptureOutbox(RiskStore store) {
    this.store = store;
  }

  public void afterCommit(long requisitionId) {
    Runnable enqueue = () -> enqueue(requisitionId);
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              enqueue.run();
            }
          });
    else enqueue.run();
  }

  void enqueue(long requisitionId) {
    try {
      store
          .db()
          .update(
              "insert into ai_outbox_event(event_type,requisition_id) values"
                  + " ('REQUISITION_COMPLETED',?) on conflict(requisition_id) do nothing",
              requisitionId);
    } catch (DataAccessException | org.springframework.web.server.ResponseStatusException error) {
      // Reconciliation recovers a missed event; AI must not reverse a completed business workflow.
      LoggerFactory.getLogger(AiCaptureOutbox.class)
          .error("Could not enqueue AI capture for requisition {}", requisitionId, error);
    }
  }
}
