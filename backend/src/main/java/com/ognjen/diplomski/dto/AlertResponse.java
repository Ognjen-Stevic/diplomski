package com.ognjen.diplomski.dto;

public record AlertResponse(
        long draftCustomers,
        long requisitionAlerts,
        long pendingGoodsRequests
) {}
