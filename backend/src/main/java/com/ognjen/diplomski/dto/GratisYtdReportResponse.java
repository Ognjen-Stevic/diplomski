package com.ognjen.diplomski.dto;

import java.util.List;

public record GratisYtdReportResponse(
        int year,
        GratisReportSummaryResponse summary,
        List<GratisProductReportResponse> products,
        List<GratisReasonReportResponse> reasons
) {
}
