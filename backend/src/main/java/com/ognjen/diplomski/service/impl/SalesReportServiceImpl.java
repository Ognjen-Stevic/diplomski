package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.domain.report.GratisAggregationCalculator;
import com.ognjen.diplomski.domain.report.GratisAggregationCalculator.GratisAggregation;
import com.ognjen.diplomski.domain.report.ReportPeriod;
import com.ognjen.diplomski.domain.report.ReportPeriodFactory;
import com.ognjen.diplomski.domain.report.ReportScopeResolver;
import com.ognjen.diplomski.domain.report.ReportScopeResolver.ReportScope;
import com.ognjen.diplomski.domain.report.SalesAggregationCalculator;
import com.ognjen.diplomski.domain.report.SalesAggregationCalculator.SalesAggregation;
import com.ognjen.diplomski.dto.GratisYtdReportResponse;
import com.ognjen.diplomski.dto.MonthlyIncentiveResponse;
import com.ognjen.diplomski.dto.MonthlyGratisReportResponse;
import com.ognjen.diplomski.dto.MonthlySalesReportResponse;
import com.ognjen.diplomski.dto.ManagerSalesComparisonResponse;
import com.ognjen.diplomski.dto.ManagersSalesComparisonResponse;
import com.ognjen.diplomski.dto.YtdSalesReportResponse;
import com.ognjen.diplomski.dto.TeamSalesComparisonResponse;
import com.ognjen.diplomski.dto.TeamSalesRepresentativeComparisonResponse;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.SalesReportRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.SalesReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class SalesReportServiceImpl implements SalesReportService {

    private final SalesReportRepository salesReportRepository;
    private final ReportScopeResolver scopeResolver;
    private final ReportPeriodFactory periodFactory = new ReportPeriodFactory();
    private final SalesAggregationCalculator salesCalculator = new SalesAggregationCalculator();
    private final GratisAggregationCalculator gratisCalculator = new GratisAggregationCalculator();

    public SalesReportServiceImpl(
            SalesReportRepository salesReportRepository,
            AppUserRepository appUserRepository
    ) {
        this.salesReportRepository = salesReportRepository;
        this.scopeResolver = new ReportScopeResolver(appUserRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlySalesReportResponse getMonthlyReport(
            AppUserPrincipal principal,
            int year,
            int month,
            Long salesRepId
    ) {
        return getMonthlyReport(principal, year, month, salesRepId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlySalesReportResponse getMonthlyReport(
            AppUserPrincipal principal,
            int year,
            int month,
            Long salesRepId,
            Long managerId
    ) {
        ReportPeriod period = periodFactory.monthly(year, month, ZoneId.systemDefault());
        ReportScope scope = scopeResolver.resolve(principal, salesRepId, managerId);
        SalesAggregation aggregation = scope.teamReport()
                ? aggregateTeamSales(scope.managerId(), period)
                : aggregateSales(scope, period);

        return new MonthlySalesReportResponse(
                year,
                month,
                scope.salesRep(),
                aggregation.summary(),
                aggregation.products()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public YtdSalesReportResponse getYtdReport(AppUserPrincipal principal, int year, Long salesRepId) {
        return getYtdReport(principal, year, salesRepId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public YtdSalesReportResponse getYtdReport(
            AppUserPrincipal principal,
            int year,
            Long salesRepId,
            Long managerId
    ) {
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime now = OffsetDateTime.now(zone);
        ReportPeriod period = periodFactory.yearToDate(year, now, zone);
        ReportScope scope = scopeResolver.resolve(principal, salesRepId, managerId);
        SalesAggregation aggregation = scope.teamReport()
                ? aggregateTeamSales(scope.managerId(), period)
                : aggregateSales(scope, period);

        int lastMonth = year == now.getYear() ? now.getMonthValue() : 12;
        List<MonthlyIncentiveResponse> monthlyIncentives = scope.teamReport()
                ? salesCalculator.teamMonthlyIncentives(
                        salesReportRepository.findTeamMonthlyIncentives(
                                scope.managerId(),
                                RequisitionType.NORMAL,
                                period.start(),
                                period.end()
                        ),
                        lastMonth
                )
                : salesCalculator.monthlyIncentivesFromRows(
                        salesReportRepository.findMonthlyIncentiveAggregates(
                                scope.salesRepId(),
                                scope.managerId(),
                                RequisitionType.NORMAL,
                                period.start(),
                                period.end()
                        ),
                        lastMonth,
                        scope.managerStimulation(),
                        scope.organizationReport()
                );

        return new YtdSalesReportResponse(
                year,
                scope.salesRep(),
                aggregation.summary(),
                aggregation.products(),
                monthlyIncentives
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TeamSalesComparisonResponse getTeamComparison(
            AppUserPrincipal principal,
            int year,
            int month
    ) {
        return getTeamComparison(principal, year, month, null);
    }

    @Override
    @Transactional(readOnly = true)
    public TeamSalesComparisonResponse getTeamComparison(
            AppUserPrincipal principal,
            int year,
            int month,
            Long requestedManagerId
    ) {
        Long managerId = scopeResolver.resolveManagerId(principal, requestedManagerId);

        ReportPeriod period = periodFactory.monthly(year, month, ZoneId.systemDefault());
        List<SalesReportRepository.TeamSalesComparisonProjection> rows =
                salesReportRepository.findTeamSalesComparison(
                        managerId,
                        period.start(),
                        period.end()
                );

        List<TeamSalesRepresentativeComparisonResponse> salesRepresentatives =
                salesCalculator.teamComparison(rows);

        return new TeamSalesComparisonResponse(
                year,
                month,
                salesRepresentatives,
                salesCalculator.managerStimulation(rows)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ManagersSalesComparisonResponse getManagersComparison(
            AppUserPrincipal principal,
            int year,
            int month
    ) {
        scopeResolver.requireManagersComparisonAccess(principal);

        ReportPeriod period = periodFactory.monthly(year, month, ZoneId.systemDefault());
        List<ManagerSalesComparisonResponse> managers = salesCalculator.managersComparison(
                salesReportRepository.findManagersSalesComparison(period.start(), period.end())
        );

        return new ManagersSalesComparisonResponse(year, month, managers);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyGratisReportResponse getMonthlyGratisReport(AppUserPrincipal principal, int year, int month) {
        return getMonthlyGratisReport(principal, year, month, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyGratisReportResponse getMonthlyGratisReport(
            AppUserPrincipal principal,
            int year,
            int month,
            Long managerId
    ) {
        return getMonthlyGratisReport(principal, year, month, null, managerId);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyGratisReportResponse getMonthlyGratisReport(
            AppUserPrincipal principal,
            int year,
            int month,
            Long salesRepId,
            Long managerId
    ) {
        ReportPeriod period = periodFactory.monthly(year, month, ZoneId.systemDefault());
        ReportScope scope = scopeResolver.resolve(principal, salesRepId, managerId);
        GratisAggregation aggregation = aggregateGratis(scope, period);

        return new MonthlyGratisReportResponse(
                year,
                month,
                aggregation.summary(),
                aggregation.products(),
                aggregation.reasons()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public GratisYtdReportResponse getGratisYtdReport(AppUserPrincipal principal, int year) {
        return getGratisYtdReport(principal, year, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public GratisYtdReportResponse getGratisYtdReport(
            AppUserPrincipal principal,
            int year,
            Long managerId
    ) {
        return getGratisYtdReport(principal, year, null, managerId);
    }

    @Override
    @Transactional(readOnly = true)
    public GratisYtdReportResponse getGratisYtdReport(
            AppUserPrincipal principal,
            int year,
            Long salesRepId,
            Long managerId
    ) {
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime now = OffsetDateTime.now(zone);
        ReportPeriod period = periodFactory.yearToDate(year, now, zone);
        ReportScope scope = scopeResolver.resolve(principal, salesRepId, managerId);
        GratisAggregation aggregation = aggregateGratis(scope, period);

        return new GratisYtdReportResponse(
                year,
                aggregation.summary(),
                aggregation.products(),
                aggregation.reasons()
        );
    }

    private SalesAggregation aggregateTeamSales(Long managerId, ReportPeriod period) {
        return salesCalculator.aggregateTeamProducts(
                salesReportRepository.findTeamProductAggregates(
                        managerId,
                        RequisitionType.NORMAL,
                        period.start(),
                        period.end()
                )
        );
    }

    private SalesAggregation aggregateSales(ReportScope scope, ReportPeriod period) {
        return salesCalculator.aggregateProductRows(
                salesReportRepository.findProductAggregates(
                        scope.salesRepId(),
                        scope.managerId(),
                        RequisitionType.NORMAL,
                        period.start(),
                        period.end()
                ),
                scope.managerStimulation(),
                scope.organizationReport()
        );
    }

    private GratisAggregation aggregateGratis(ReportScope scope, ReportPeriod period) {
        return gratisCalculator.aggregateRows(
                salesReportRepository.findGratisProductAggregates(
                        scope.salesRepId(), scope.managerId(), period.start(), period.end()),
                salesReportRepository.findGratisReasonAggregates(
                        scope.salesRepId(), scope.managerId(), period.start(), period.end())
        );
    }

}
