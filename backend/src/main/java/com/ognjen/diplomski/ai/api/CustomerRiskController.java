package com.ognjen.diplomski.ai.api;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;

import com.ognjen.diplomski.ai.service.CustomerRiskService;
import com.ognjen.diplomski.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@Validated
@RequiredArgsConstructor
public class CustomerRiskController {
  private final CustomerRiskService service;

  @GetMapping("/customers/catalog")
  public com.ognjen.diplomski.dto.CustomerPageResponse catalog(
      @AuthenticationPrincipal AppUserPrincipal user,
      @RequestParam(required = false) String search,
      @RequestParam Level risk,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
    return service.catalog(user, search, risk, page, size);
  }

  @PostMapping("/customers/risks/batch")
  public List<RiskBatchItem> batch(
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
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
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
  public List<ActivityItem> activities(
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

}
