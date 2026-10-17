package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.GratisReason;

import java.math.BigDecimal;

public record GratisReasonReportResponse(
        GratisReason reason,
        BigDecimal totalLiters
) {
}
