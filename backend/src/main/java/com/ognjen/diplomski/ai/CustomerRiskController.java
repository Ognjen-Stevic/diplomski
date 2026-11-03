package com.ognjen.diplomski.ai;

import static com.ognjen.diplomski.ai.RiskTypes.*;

import com.ognjen.diplomski.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
@Validated
@RequiredArgsConstructor
public class CustomerRiskController {
  private final CustomerRiskService service;
  private final RiskJobs jobs;

  @GetMapping("/customers/catalog")
  public com.ognjen.diplomski.dto.CustomerPageResponse catalog(
      @AuthenticationPrincipal AppUserPrincipal user,
      @RequestParam(required = false) String search,
      @RequestParam Level risk,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    return service.catalog(user, search, risk, page, size);
  }

  @PostMapping("/customers/risks/batch")
  public List<Assessment> batch(
      @AuthenticationPrincipal AppUserPrincipal user,
      @RequestBody @jakarta.validation.constraints.Size(max = 100)
          List<@NotNull @Positive Long> ids) {
    return service.batch(user, ids);
  }

  @GetMapping("/customers/risks")
  public RiskPage list(
      @AuthenticationPrincipal AppUserPrincipal user,
      @RequestParam(required = false) Long salesRepId,
      @RequestParam(required = false) Long managerId,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Level risk,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    return service.list(user, salesRepId, managerId, search, risk, page, size);
  }

  @GetMapping("/summary")
  public Summary summary(
      @AuthenticationPrincipal AppUserPrincipal user,
      @RequestParam(required = false) Long salesRepId,
      @RequestParam(required = false) Long managerId) {
    return service.summary(user, salesRepId, managerId);
  }

  @GetMapping("/customers/{id}/risk")
  public Assessment detail(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id) {
    return service.detail(user, id);
  }

  @GetMapping("/customers/{id}/activities")
  public List<Map<String, Object>> activities(
      @AuthenticationPrincipal AppUserPrincipal user, @PathVariable long id) {
    return service.activities(user, id);
  }

  @PostMapping("/customers/{id}/activities")
  @ResponseStatus(HttpStatus.CREATED)
  public void activity(
      @AuthenticationPrincipal AppUserPrincipal user,
      @PathVariable long id,
      @Valid @RequestBody ActivityRequest request) {
    service.activity(user, id, request);
  }

  @GetMapping("/risk-trends")
  public List<Map<String, Object>> trends(
      @AuthenticationPrincipal AppUserPrincipal user,
      @RequestParam(required = false) Long salesRepId,
      @RequestParam(required = false) Long managerId) {
    // Authorization is inside the same scoped query as list/summary, never on a frontend-provided
    // user ID.
    return service.trends(user, salesRepId, managerId);
  }

  @PostMapping("/admin/{operation}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, UUID> start(
      @AuthenticationPrincipal AppUserPrincipal user, @PathVariable String operation) {
    RiskJobs.admin(user);
    if (!Set.of("train", "refresh").contains(operation))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI operation not found");
    return Map.of("jobId", jobs.start(user, operation));
  }

  @GetMapping("/admin/jobs/{id}")
  public Map<String, Object> job(
      @AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
    return jobs.job(user, id);
  }

  @GetMapping("/admin/models")
  public List<Map<String, Object>> models(@AuthenticationPrincipal AppUserPrincipal user) {
    return jobs.models(user);
  }

  @PostMapping("/admin/models/{id}/activate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void activate(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
    jobs.activate(user, id);
  }
}
