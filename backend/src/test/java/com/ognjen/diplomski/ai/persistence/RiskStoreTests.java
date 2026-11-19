package com.ognjen.diplomski.ai.persistence;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class RiskStoreTests {

  @Test
  void cachesOnlySuccessfulSchemaValidation() {
    @SuppressWarnings("unchecked")
    ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(provider.getIfAvailable()).thenReturn(jdbc);
    when(jdbc.queryForObject("select to_regclass('ai_model') is not null", Boolean.class))
        .thenReturn(true);
    RiskStore store = new RiskStore(provider, mock(ObjectMapper.class));

    assertSame(jdbc, store.db());
    assertSame(jdbc, store.db());

    verify(jdbc).queryForObject("select to_regclass('ai_model') is not null", Boolean.class);
  }

  @Test
  void unavailableSchemaIsRecheckedAndKeepsExistingServiceUnavailableBehavior() {
    @SuppressWarnings("unchecked")
    ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(provider.getIfAvailable()).thenReturn(jdbc);
    when(jdbc.queryForObject("select to_regclass('ai_model') is not null", Boolean.class))
        .thenReturn(false, true);
    RiskStore store = new RiskStore(provider, mock(ObjectMapper.class));

    assertThrows(ResponseStatusException.class, store::db);
    assertSame(jdbc, store.db());
  }
}
