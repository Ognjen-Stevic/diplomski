package com.ognjen.diplomski.ai.audit;

import com.ognjen.diplomski.ai.support.AiErrorMessages;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Type-safe, best-effort JSONL audit sink. PostgreSQL remains the system of record. */
@Component
public class AiAuditLogger {
  public enum Severity { INFO, WARN, ERROR }

  public record Context(
      String correlationId,
      UUID jobId,
      UUID modelId,
      String modelVersion,
      Long customerId,
      String status,
      Long durationMs) {
    public static Context empty(String status) {
      return new Context(null, null, null, null, null, status, null);
    }
  }

  private static final Logger DECISIONS = LoggerFactory.getLogger("AI_DECISIONS");
  private static final Logger TRAINING = LoggerFactory.getLogger("AI_TRAINING");
  private static final Logger LIFECYCLE = LoggerFactory.getLogger("AI_LIFECYCLE");
  private static final Logger ERRORS = LoggerFactory.getLogger("AI_ERRORS");
  private static final Logger FALLBACK = LoggerFactory.getLogger(AiAuditLogger.class);
  private final ObjectMapper json;

  public AiAuditLogger(ObjectMapper json) {
    this.json = json;
  }

  public void decision(String eventType, Severity severity, Context context, Map<String, ?> details) {
    write(DECISIONS, eventType, severity, context, details);
  }

  public void training(String eventType, Severity severity, Context context, Map<String, ?> details) {
    write(TRAINING, eventType, severity, context, details);
  }

  public void lifecycle(String eventType, Severity severity, Context context, Map<String, ?> details) {
    write(LIFECYCLE, eventType, severity, context, details);
  }

  public void error(
      String eventType, Context context, String category, Throwable error, Map<String, ?> details) {
    var sanitized = new LinkedHashMap<String, Object>();
    if (details != null) sanitized.putAll(details);
    sanitized.put("errorCategory", category);
    sanitized.put("exceptionType", error.getClass().getSimpleName());
    sanitized.put("error", AiErrorMessages.safe(error instanceof Exception e ? e : new Exception(error)));
    write(ERRORS, eventType, Severity.ERROR, context, sanitized);
  }

  String render(String eventType, Severity severity, Context context, Map<String, ?> details)
      throws JacksonException {
    var event = new LinkedHashMap<String, Object>();
    event.put("timestamp", Instant.now());
    event.put("eventType", eventType);
    event.put("severity", severity.name());
    event.put("correlationId", context.correlationId());
    event.put("jobId", context.jobId());
    event.put("modelId", context.modelId());
    event.put("modelVersion", context.modelVersion());
    event.put("customerId", context.customerId());
    event.put("status", context.status());
    event.put("durationMs", context.durationMs());
    event.put("details", details == null ? Map.of() : details);
    return json.writeValueAsString(event);
  }

  private void write(
      Logger target, String eventType, Severity severity, Context context, Map<String, ?> details) {
    try {
      target.info(render(eventType, severity, context, details));
    } catch (Exception auditFailure) {
      // Audit I/O/serialization must never change an AI database transaction or scheduler outcome.
      FALLBACK.error("AI audit event {} could not be written", eventType, auditFailure);
    }
  }
}
