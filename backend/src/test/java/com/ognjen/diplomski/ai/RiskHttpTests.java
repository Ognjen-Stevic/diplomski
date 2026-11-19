package com.ognjen.diplomski.ai;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ognjen.diplomski.auth.*;
import com.ognjen.diplomski.ai.api.CustomerRiskController;
import com.ognjen.diplomski.ai.persistence.RiskStore;
import com.ognjen.diplomski.ai.service.CustomerRiskService;
import com.ognjen.diplomski.config.SpringSecurityConfig;
import com.ognjen.diplomski.entity.*;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerRiskController.class)
@Import({
  SpringSecurityConfig.class,
  JwtAuthenticationFilter.class,
  JwtService.class,
  ApiErrorWriter.class,
  ApiExceptionHandler.class,
  CustomerRiskService.class,
  RiskStore.class
})
@TestPropertySource(
    properties = {
      "security.jwt.secret=ai-http-tests-only-secret-at-least-32-bytes",
      "logging.level.root=WARN",
      "debug=false"
    })
class RiskHttpTests {
  @Autowired MockMvc mvc;
  @Autowired JwtService jwt;
  @MockitoBean AppUserRepository users;
  @MockitoBean com.ognjen.diplomski.repository.CustomerRepository customers;
  @MockitoBean JdbcTemplate db;

  private String token(String role) {
    AppUser user = new AppUser();
    user.setId(7L);
    user.setEmail("test@example.test");
    user.setPasswordHash("unused");
    Role assigned = new Role();
    assigned.setName(role);
    Permission permission = new Permission();
    permission.setName(Permissions.CUSTOMERS_READ);
    assigned.getPermissions().add(permission);
    user.setRole(assigned);
    when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
    return "Bearer " + jwt.generateToken(AppUserPrincipal.from(user));
  }

  @Test
  void anonymousAndBookkeeperAreDenied() throws Exception {
    mvc.perform(get("/api/ai/summary")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/ai/summary").header("Authorization", token("BOOKKEEPER")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(db);
  }

  @Test
  void noMigrationIsAnExplicitServiceUnavailableState() throws Exception {
    mvc.perform(get("/api/ai/summary").header("Authorization", token("CEO")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.message")
                .value("AI migration V001 must be applied before using analytics"));
  }

  @Test
  void noRoleCanInvokeRemovedLifecycleEndpoints() throws Exception {
    for (String role : List.of("MANAGER", "SALES_REP", "BOOKKEEPER")) {
      mvc.perform(post("/api/ai/admin/train").header("Authorization", token(role)))
          .andExpect(status().isForbidden());
      mvc.perform(post("/api/ai/admin/refresh").header("Authorization", token(role)))
          .andExpect(status().isForbidden());
      mvc.perform(
              post("/api/ai/admin/models/" + UUID.randomUUID() + "/activate")
                  .header("Authorization", token(role)))
          .andExpect(status().isForbidden());
    }
    String ceo = token("CEO");
    mvc.perform(post("/api/ai/admin/train").header("Authorization", ceo))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/ai/admin/refresh").header("Authorization", ceo))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/ai/admin/models/" + UUID.randomUUID() + "/activate")
                .header("Authorization", ceo))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/ai/admin/models").header("Authorization", ceo))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/ai/admin/monitoring").header("Authorization", ceo))
        .andExpect(status().isNotFound());
  }

  @Test
  void ownerScopesAreAppliedInSqlAndCannotBeBroadened() throws Exception {
    when(db.queryForObject("select to_regclass('ai_model') is not null", Boolean.class))
        .thenReturn(true);
    mvc.perform(get("/api/ai/customers/risks").header("Authorization", token("SALES_REP")))
        .andExpect(status().isOk());
    verify(db).queryForObject(contains("c.sales_rep_id=?"), eq(Long.class), any(Object[].class));
    mvc.perform(
            get("/api/ai/customers/risks?salesRepId=99")
                .header("Authorization", token("SALES_REP")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/ai/customers/risks?managerId=99").header("Authorization", token("MANAGER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void teamScopesUseCurrentManagerAndCompanyUsesAllVisibleCustomers() throws Exception {
    when(db.queryForObject("select to_regclass('ai_model') is not null", Boolean.class))
        .thenReturn(true);
    mvc.perform(get("/api/ai/customers/risks").header("Authorization", token("MANAGER")))
        .andExpect(status().isOk());
    verify(db).queryForObject(contains("u.manager_id=?"), eq(Long.class), any(Object[].class));
    mvc.perform(get("/api/ai/customers/risks").header("Authorization", token("CEO")))
        .andExpect(status().isOk());
    verify(db).queryForObject(contains("and (true)"), eq(Long.class), any(Object[].class));
  }

  @Test
  void batchIsBoundedAndScopedAndUnrelatedDetailIsNotFound() throws Exception {
    when(db.queryForObject("select to_regclass('ai_model') is not null", Boolean.class))
        .thenReturn(true);
    mvc.perform(
            post("/api/ai/customers/risks/batch")
                .header("Authorization", token("SALES_REP"))
                .contentType("application/json")
                .content("[1,2]"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    mvc.perform(get("/api/ai/customers/99/risk").header("Authorization", token("SALES_REP")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/ai/customers/risks?size=1000").header("Authorization", token("CEO")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void riskFilterIsAppliedBeforePagination() throws Exception {
    when(db.queryForObject("select to_regclass('ai_model') is not null", Boolean.class))
        .thenReturn(true);
    mvc.perform(
            get("/api/ai/customers/risks?risk=HIGH&page=1").header("Authorization", token("CEO")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(20));
    verify(db)
        .queryForObject(
            contains("coalesce(a.level,'INSUFFICIENT_DATA')=?"),
            eq(Long.class),
            any(Object[].class));
  }
}
