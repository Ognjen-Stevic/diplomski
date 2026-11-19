package com.ognjen.diplomski.ai.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AiAuditLoggerTests {
  @Test
  void rendersOneValidStructuredJsonObjectWithoutSensitiveApplicationData() throws Exception {
    var audit = new AiAuditLogger(new ObjectMapper());
    String line =
        audit.render(
            "MODEL_PROMOTED",
            AiAuditLogger.Severity.INFO,
            new AiAuditLogger.Context(
                "correlation-1", UUID.randomUUID(), UUID.randomUUID(), "V004", 42L,
                "PROMOTED", 17L),
            Map.of("macroF1", 0.81, "reason", "validation passed"));

    var event = new ObjectMapper().readTree(line);
    assertEquals("MODEL_PROMOTED", event.get("eventType").asText());
    assertEquals("V004", event.get("modelVersion").asText());
    assertEquals(42L, event.get("customerId").asLong());
    assertEquals(17L, event.get("durationMs").asLong());
    assertFalse(line.toLowerCase().contains("password"));
    assertFalse(line.toLowerCase().contains("token"));
    assertFalse(line.contains("test@example"));
  }
}
