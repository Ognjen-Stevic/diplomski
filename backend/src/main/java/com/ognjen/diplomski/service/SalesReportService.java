package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.GratisYtdReportResponse;
import com.ognjen.diplomski.dto.MonthlySalesReportResponse;
import com.ognjen.diplomski.dto.ManagersSalesComparisonResponse;
import com.ognjen.diplomski.dto.MonthlyGratisReportResponse;
import com.ognjen.diplomski.dto.YtdSalesReportResponse;
import com.ognjen.diplomski.dto.TeamSalesComparisonResponse;
import com.ognjen.diplomski.security.AppUserPrincipal;

public interface SalesReportService {

    MonthlySalesReportResponse getMonthlyReport(
            AppUserPrincipal principal,
            int year,
            int month,
            Long salesRepId
    );

    MonthlySalesReportResponse getMonthlyReport(
            AppUserPrincipal principal,
            int year,
            int month,
            Long salesRepId,
            Long managerId
    );

    YtdSalesReportResponse getYtdReport(AppUserPrincipal principal, int year, Long salesRepId);

    YtdSalesReportResponse getYtdReport(
            AppUserPrincipal principal,
            int year,
            Long salesRepId,
            Long managerId
    );

    TeamSalesComparisonResponse getTeamComparison(
            AppUserPrincipal principal,
            int year,
            int month
    );

    ManagersSalesComparisonResponse getManagersComparison(
            AppUserPrincipal principal,
            int year,
            int month
    );

    TeamSalesComparisonResponse getTeamComparison(
            AppUserPrincipal principal,
            int year,
            int month,
            Long managerId
    );

    MonthlyGratisReportResponse getMonthlyGratisReport(AppUserPrincipal principal, int year, int month);

    MonthlyGratisReportResponse getMonthlyGratisReport(
            AppUserPrincipal principal,
            int year,
            int month,
            Long managerId
    );

    MonthlyGratisReportResponse getMonthlyGratisReport(
            AppUserPrincipal principal,
            int year,
            int month,
            Long salesRepId,
            Long managerId
    );

    GratisYtdReportResponse getGratisYtdReport(AppUserPrincipal principal, int year);

    GratisYtdReportResponse getGratisYtdReport(
            AppUserPrincipal principal,
            int year,
            Long managerId
    );

    GratisYtdReportResponse getGratisYtdReport(
            AppUserPrincipal principal,
            int year,
            Long salesRepId,
            Long managerId
    );
}
