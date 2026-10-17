package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.GoodsRequestItemStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record GoodsRequestRowResponse(
        Long id,
        SalesRepresentativeOptionResponse salesRep,
        List<GoodsRequestItemResponse> items,
        String note,
        OffsetDateTime createdAt,
        GoodsRequestItemStatus status,
        boolean hasPendingItems
) {
}
