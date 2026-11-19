package com.ognjen.diplomski.dto;

public record DashboardMonthlyStatisticsResponse(
        long completedCount,
        long completedGratisCount,
        long cancelledCount
) {
}
