package com.ognjen.diplomski.contorller;

import com.ognjen.diplomski.dto.GoodsRequestRowResponse;
import com.ognjen.diplomski.dto.GoodsRequestRequest;
import com.ognjen.diplomski.dto.GoodsRequestStatusRequest;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.GoodsRequestService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/goods-requests")
@RequiredArgsConstructor
@Validated
public class GoodsRequestController {

    private final GoodsRequestService goodsRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoodsRequestRowResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody GoodsRequestRequest request
    ) {
        return goodsRequestService.create(principal, request);
    }

    @GetMapping
    public List<GoodsRequestRowResponse> getMonthlyGoodsRequests(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month
    ) {
        return goodsRequestService.getMonthlyGoodsRequests(principal, year, month);
    }

    @PatchMapping("/{id}/status")
    public GoodsRequestRowResponse updateStatus(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody GoodsRequestStatusRequest request
    ) {
        return goodsRequestService.updateStatus(principal, id, request.status());
    }
}
