package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.GoodsRequestRowResponse;
import com.ognjen.diplomski.dto.GoodsRequestRequest;
import com.ognjen.diplomski.entity.enums.GoodsRequestItemStatus;
import com.ognjen.diplomski.security.AppUserPrincipal;

import java.util.List;

public interface GoodsRequestService {

    GoodsRequestRowResponse create(
            AppUserPrincipal principal,
            GoodsRequestRequest request
    );

    List<GoodsRequestRowResponse> getMonthlyGoodsRequests(
            AppUserPrincipal principal,
            int year,
            int month
    );

    GoodsRequestRowResponse updateStatus(
            AppUserPrincipal principal,
            Long goodsRequestId,
            GoodsRequestItemStatus status
    );
}
