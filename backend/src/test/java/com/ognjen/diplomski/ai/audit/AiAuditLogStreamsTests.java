package com.ognjen.diplomski.ai.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "ai.lifecycle.scheduling.enabled=false",
      "app.ai.audit.path=target/test-ai-logs",
      "logging.level.root=WARN",
      "debug=false"
    })
class AiAuditLogStreamsTests {
  @Autowired AiAuditLogger audit;
  @Autowired ObjectMapper json;

  @Test
  void writesFourIndependentValidJsonlStreams() throws Exception {
    var context = AiAuditLogger.Context.empty("TEST");
    audit.decision("DECISION_TEST", AiAuditLogger.Severity.INFO, context, Map.of("feature", 1));
    audit.training("TRAINING_TEST", AiAuditLogger.Severity.INFO, context, Map.of("samples", 150));
    audit.lifecycle("LIFECYCLE_TEST", AiAuditLogger.Severity.INFO, context, Map.of("operation", "capture"));
    audit.error("ERROR_TEST", context, "TEST", new IllegalStateException("sanitized"), Map.of());

    for (String file :
        List.of(
            "ai-decisions.jsonl",
            "ai-training.jsonl",
            "ai-lifecycle.jsonl",
            "ai-errors.jsonl")) {
      Path path = Path.of("target", "test-ai-logs", file);
      assertTrue(Files.exists(path), () -> "Missing audit stream " + path);
      String latest = Files.readAllLines(path).getLast();
      assertTrue(json.readTree(latest).has("eventType"));
    }
  }
}
