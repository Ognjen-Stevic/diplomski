package com.ognjen.diplomski.dto;

import java.util.List;

public record MonthlyGratisReportResponse(
        int year,
        int month,
        GratisReportSummaryResponse summary,
        List<GratisProductReportResponse> products,
        List<GratisReasonReportResponse> reasons
) {
}
