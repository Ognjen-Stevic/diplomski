package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.dto.AdvertisingMaterialResponse;
import com.ognjen.diplomski.dto.AdvertisingMaterialDistributionResponse;
import com.ognjen.diplomski.dto.CreateAdvertisingMaterialDistributionRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialCreateRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialActiveRequest;
import com.ognjen.diplomski.dto.AdvertisingMaterialManagementResponse;
import com.ognjen.diplomski.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.ognjen.diplomski.service.AdvertisingMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/advertising-materials")
@RequiredArgsConstructor
@Validated
public class AdvertisingMaterialController {

    private final AdvertisingMaterialService advertisingMaterialService;

    @GetMapping
    public List<AdvertisingMaterialManagementResponse> getAdvertisingMaterials(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return advertisingMaterialService.getAdvertisingMaterials(principal);
    }

    @GetMapping("/available")
    public List<AdvertisingMaterialResponse> getAvailableAdvertisingMaterials() {
        return advertisingMaterialService.getAvailableAdvertisingMaterials();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdvertisingMaterialManagementResponse createAdvertisingMaterial(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody AdvertisingMaterialCreateRequest request
    ) {
        return advertisingMaterialService.createAdvertisingMaterial(principal, request);
    }

    @PatchMapping("/{materialId}/active")
    public AdvertisingMaterialManagementResponse setAdvertisingMaterialActive(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long materialId,
            @Valid @RequestBody AdvertisingMaterialActiveRequest request
    ) {
        return advertisingMaterialService.setAdvertisingMaterialActive(
                principal, materialId, request
        );
    }

    @PostMapping("/distributions")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AdvertisingMaterialDistributionResponse> recordAdvertisingMaterialDistribution(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreateAdvertisingMaterialDistributionRequest request
    ) {
        return advertisingMaterialService.recordAdvertisingMaterialDistribution(principal, request);
    }

    @GetMapping("/distributions")
    public List<AdvertisingMaterialDistributionResponse> getMonthlyAdvertisingMaterials(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month
    ) {
        return advertisingMaterialService.getMonthlyAdvertisingMaterials(principal, year, month);
    }
}
