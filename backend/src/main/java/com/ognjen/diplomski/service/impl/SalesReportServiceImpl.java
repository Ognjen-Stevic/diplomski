package com.ognjen.diplomski.service.impl;

import com.ognjen.diplomski.dto.GratisProductReportResponse;
import com.ognjen.diplomski.dto.GratisReasonReportResponse;
import com.ognjen.diplomski.dto.GratisReportSummaryResponse;
import com.ognjen.diplomski.dto.GratisYtdReportResponse;
import com.ognjen.diplomski.dto.MonthlyIncentiveResponse;
import com.ognjen.diplomski.dto.MonthlyGratisReportResponse;
import com.ognjen.diplomski.dto.MonthlySalesReportResponse;
import com.ognjen.diplomski.dto.ManagerSalesComparisonResponse;
import com.ognjen.diplomski.dto.ManagersSalesComparisonResponse;
import com.ognjen.diplomski.dto.ProductSalesReportResponse;
import com.ognjen.diplomski.dto.SalesReportSummaryResponse;
import com.ognjen.diplomski.dto.SalesRepresentativeOptionResponse;
import com.ognjen.diplomski.dto.YtdSalesReportResponse;
import com.ognjen.diplomski.dto.TeamSalesComparisonResponse;
import com.ognjen.diplomski.dto.TeamSalesRepresentativeComparisonResponse;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.PackageType;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.repository.AppUserRepository;
import com.ognjen.diplomski.repository.SalesReportRepository;
import com.ognjen.diplomski.security.AppUserPrincipal;
import com.ognjen.diplomski.service.SalesReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SalesReportServiceImpl implements SalesReportService {

    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal ZERO_LITERS = BigDecimal.ZERO.setScale(3);

    private final SalesReportRepository salesReportRepository;
    private final AppUserRepository appUserRepository;

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
        YearMonth period = YearMonth.of(year, month);
        DateRange range = monthRange(period);
        ReportScope scope = resolveScope(principal, salesRepId, managerId);
        SalesAggregation aggregation = scope.teamReport()
                ? aggregateTeamSales(scope.managerId(), range)
                : aggregateSales(
                        findForScope(scope, RequisitionType.NORMAL, range),
                        scope.managerStimulation(), scope.organizationReport()
                );

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
        DateRange range = yearRange(year, now, zone);
        ReportScope scope = resolveScope(principal, salesRepId, managerId);
        List<Requisition> requisitions = scope.teamReport()
                ? List.of()
                : findForScope(scope, RequisitionType.NORMAL, range);
        SalesAggregation aggregation = scope.teamReport()
                ? aggregateTeamSales(scope.managerId(), range)
                : aggregateSales(requisitions, scope.managerStimulation(), scope.organizationReport());

        Map<Integer, BigDecimal> incentivesByMonth = new LinkedHashMap<>();
        int lastMonth = year == now.getYear() ? now.getMonthValue() : 12;
        for (int month = 1; month <= lastMonth; month++) {
            incentivesByMonth.put(month, ZERO_MONEY);
        }
        if (scope.teamReport()) {
            salesReportRepository.findTeamMonthlyIncentives(
                    scope.managerId(),
                    RequisitionType.NORMAL,
                    range.start(),
                    range.end()
            ).forEach(row -> incentivesByMonth.put(row.getMonth(), money(row.getTotalIncentive())));
        } else {
            for (Requisition requisition : requisitions) {
                BigDecimal incentive = requisition.getItems().stream()
                        .map(item -> itemIncentive(item, scope.managerStimulation(), scope.organizationReport()))
                        .reduce(ZERO_MONEY, BigDecimal::add);
                incentivesByMonth.merge(requisition.getCreatedAt().getMonthValue(), incentive, BigDecimal::add);
            }
        }
        List<MonthlyIncentiveResponse> monthlyIncentives = incentivesByMonth.entrySet().stream()
                .map(entry -> new MonthlyIncentiveResponse(entry.getKey(), money(entry.getValue())))
                .toList();

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
        Long managerId = resolveManagerId(principal, requestedManagerId);

        DateRange range = monthRange(YearMonth.of(year, month));
        List<SalesReportRepository.TeamSalesComparisonProjection> rows =
                salesReportRepository.findTeamSalesComparison(
                        managerId,
                        range.start(),
                        range.end()
                );

        List<TeamSalesRepresentativeComparisonResponse> salesRepresentatives = rows.stream()
                .map(row -> new TeamSalesRepresentativeComparisonResponse(
                        row.getSalesRepId(),
                        row.getFirstName(),
                        row.getLastName(),
                        quantity(row.getBottlesSold()),
                        quantity(row.getBalloonsSold()),
                        quantity(row.getGratisBottles()),
                        quantity(row.getGratisBalloons()),
                        money(row.getStimulation())
                ))
                .toList();
        BigDecimal managerStimulation = rows.stream()
                .map(SalesReportRepository.TeamSalesComparisonProjection::getManagerStimulation)
                .reduce(ZERO_MONEY, BigDecimal::add);

        return new TeamSalesComparisonResponse(
                year,
                month,
                salesRepresentatives,
                money(managerStimulation)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ManagersSalesComparisonResponse getManagersComparison(
            AppUserPrincipal principal,
            int year,
            int month
    ) {
        if (!principal.hasRole("CEO")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CEO can compare managers");
        }

        DateRange range = monthRange(YearMonth.of(year, month));
        List<ManagerSalesComparisonResponse> managers = salesReportRepository
                .findManagersSalesComparison(range.start(), range.end())
                .stream()
                .map(row -> new ManagerSalesComparisonResponse(
                        row.getManagerId(),
                        row.getFirstName(),
                        row.getLastName(),
                        quantity(row.getBottlesSold()),
                        quantity(row.getBalloonsSold()),
                        quantity(row.getGratisBottles()),
                        quantity(row.getGratisBalloons()),
                        money(row.getStimulation())
                ))
                .toList();

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
        YearMonth period = YearMonth.of(year, month);
        DateRange range = monthRange(period);
        ReportScope scope = resolveScope(principal, salesRepId, managerId);
        List<Requisition> requisitions = scope.teamReport()
                ? findTeam(scope.managerId(), RequisitionType.GRATIS, range)
                : findForScope(scope, RequisitionType.GRATIS, range);
        GratisAggregation aggregation = aggregateGratis(requisitions);

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
        DateRange range = yearRange(year, now, zone);
        ReportScope scope = resolveScope(principal, salesRepId, managerId);
        List<Requisition> requisitions = scope.teamReport()
                ? findTeam(scope.managerId(), RequisitionType.GRATIS, range)
                : findForScope(scope, RequisitionType.GRATIS, range);
        GratisAggregation aggregation = aggregateGratis(requisitions);

        return new GratisYtdReportResponse(
                year,
                aggregation.summary(),
                aggregation.products(),
                aggregation.reasons()
        );
    }

    private static GratisAggregation aggregateGratis(List<Requisition> requisitions) {

        Map<Long, ProductAccumulator> products = new LinkedHashMap<>();
        Map<GratisReason, BigDecimal> litersByReason = new EnumMap<>(GratisReason.class);
        BigDecimal totalItems = BigDecimal.ZERO;
        BigDecimal totalBottles = BigDecimal.ZERO;
        BigDecimal totalJugs = BigDecimal.ZERO;
        BigDecimal totalLiters = BigDecimal.ZERO;

        for (Requisition requisition : requisitions) {
            BigDecimal requisitionLiters = BigDecimal.ZERO;
            for (RequisitionItem item : requisition.getItems()) {
                Product product = item.getProduct();
                products.computeIfAbsent(product.getId(), ignored -> new ProductAccumulator(product, false))
                        .add(item);
                totalItems = totalItems.add(item.getQuantity());
                if (product.getPackageType() == PackageType.BOTTLE) {
                    totalBottles = totalBottles.add(item.getQuantity());
                } else if (product.getPackageType() == PackageType.JUG) {
                    totalJugs = totalJugs.add(item.getQuantity());
                }
                BigDecimal itemLiters = itemLiters(item);
                totalLiters = totalLiters.add(itemLiters);
                requisitionLiters = requisitionLiters.add(itemLiters);
            }
            GratisReason reason = requisition.getGratisReason() == null
                    ? GratisReason.OTHER
                    : requisition.getGratisReason();
            litersByReason.merge(reason, requisitionLiters, BigDecimal::add);
        }

        List<GratisProductReportResponse> productRows = products.values().stream()
                .map(ProductAccumulator::toGratisResponse)
                .toList();
        List<GratisReasonReportResponse> reasonRows = litersByReason.entrySet().stream()
                .map(entry -> new GratisReasonReportResponse(entry.getKey(), liters(entry.getValue())))
                .toList();

        return new GratisAggregation(
                new GratisReportSummaryResponse(
                        quantity(totalItems),
                        quantity(totalBottles),
                        quantity(totalJugs),
                        liters(totalLiters)
                ),
                productRows,
                reasonRows
        );
    }

    private List<Requisition> find(Long salesRepId, RequisitionType type, DateRange range) {
        return salesReportRepository.findForReport(salesRepId, type, range.start(), range.end());
    }

    private List<Requisition> findForScope(ReportScope scope, RequisitionType type, DateRange range) {
        if (scope.organizationReport()) {
            return salesReportRepository.findAllForReport(type, range.start(), range.end());
        }
        return find(scope.salesRepId(), type, range);
    }

    private List<Requisition> findTeam(Long managerId, RequisitionType type, DateRange range) {
        return salesReportRepository.findTeamForReport(managerId, type, range.start(), range.end());
    }

    private ReportScope resolveScope(
            AppUserPrincipal principal,
            Long requestedSalesRepId,
            Long requestedManagerId
    ) {
        if (principal.hasRole("CEO")) {
            if (requestedManagerId == null) {
                if (requestedSalesRepId == null) {
                    return new ReportScope(null, null, null, false, false, true);
                }
                SalesRepresentativeOptionResponse salesRep = appUserRepository
                        .findByIdAndRoleNameIgnoreCase(requestedSalesRepId, "SALES_REP")
                        .map(SalesRepresentativeOptionResponse::from)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Sales representative not found"
                        ));
                return new ReportScope(requestedSalesRepId, salesRep, null, false, false, false);
            }

            Long managerId = resolveManagerId(principal, requestedManagerId);
            if (requestedSalesRepId == null) {
                return new ReportScope(null, null, managerId, true, true, false);
            }
            SalesRepresentativeOptionResponse salesRep = appUserRepository
                    .findByIdAndManagerIdAndRoleNameIgnoreCase(
                            requestedSalesRepId,
                            managerId,
                            "SALES_REP"
                    )
                    .map(SalesRepresentativeOptionResponse::from)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Sales representative not found"
                    ));
            return new ReportScope(requestedSalesRepId, salesRep, managerId, false, true, false);
        }

        if (principal.hasRole("MANAGER")) {
            Long managerId = resolveManagerId(principal, requestedManagerId);
            if (requestedSalesRepId == null) {
                return new ReportScope(null, null, managerId, true, true, false);
            }
            SalesRepresentativeOptionResponse salesRep = appUserRepository
                    .findByIdAndManagerIdAndRoleNameIgnoreCase(
                            requestedSalesRepId,
                            managerId,
                            "SALES_REP"
                    )
                    .map(SalesRepresentativeOptionResponse::from)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Sales representative not found"
                    ));
            return new ReportScope(requestedSalesRepId, salesRep, managerId, false, true, false);
        }

        if (requestedManagerId != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only CEO can select a manager");
        }

        if (requestedSalesRepId != null && !requestedSalesRepId.equals(principal.id())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "A sales representative can only view their own report"
            );
        }
        return new ReportScope(
                principal.id(),
                new SalesRepresentativeOptionResponse(
                        principal.id(),
                        principal.firstName(),
                        principal.lastName()
                ),
                null,
                false,
                false,
                false
        );
    }

    private Long resolveManagerId(AppUserPrincipal principal, Long requestedManagerId) {
        if (principal.hasRole("MANAGER")) {
            if (requestedManagerId != null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Managers cannot select managerId");
            }
            return principal.id();
        }
        if (principal.hasRole("CEO")) {
            if (requestedManagerId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "managerId is required");
            }
            appUserRepository.findByIdAndRoleNameIgnoreCase(requestedManagerId, "MANAGER")
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manager not found"));
            return requestedManagerId;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only managers and CEO can access team reports");
    }

    private SalesAggregation aggregateTeamSales(Long managerId, DateRange range) {
        List<ProductSalesReportResponse> products = salesReportRepository.findTeamProductAggregates(
                        managerId,
                        RequisitionType.NORMAL,
                        range.start(),
                        range.end()
                ).stream()
                .map(row -> new ProductSalesReportResponse(
                        row.getProductCode(),
                        row.getProductName(),
                        row.getPictureUrl(),
                        quantity(row.getUnits()),
                        row.getVolumeLiters(),
                        row.getPackageType(),
                        money(row.getIncentivePerItem()),
                        money(row.getTotalIncentive())
                ))
                .toList();

        BigDecimal totalItems = BigDecimal.ZERO;
        BigDecimal totalBottles = BigDecimal.ZERO;
        BigDecimal totalJugs = BigDecimal.ZERO;
        BigDecimal totalLiters = BigDecimal.ZERO;
        BigDecimal totalIncentive = BigDecimal.ZERO;
        for (ProductSalesReportResponse product : products) {
            totalItems = totalItems.add(product.units());
            if (product.packageType() == PackageType.BOTTLE) {
                totalBottles = totalBottles.add(product.units());
            } else if (product.packageType() == PackageType.JUG) {
                totalJugs = totalJugs.add(product.units());
            }
            totalLiters = totalLiters.add(product.units().multiply(product.volumeLiters()));
            totalIncentive = totalIncentive.add(product.totalIncentive());
        }

        return new SalesAggregation(
                new SalesReportSummaryResponse(
                        quantity(totalItems),
                        quantity(totalBottles),
                        quantity(totalJugs),
                        liters(totalLiters),
                        money(totalIncentive)
                ),
                products
        );
    }

    private static SalesAggregation aggregateSales(
            List<Requisition> requisitions,
            boolean managerStimulation, boolean companyReport
    ) {
        Map<Long, ProductAccumulator> products = new LinkedHashMap<>();
        BigDecimal totalItems = BigDecimal.ZERO;
        BigDecimal totalBottles = BigDecimal.ZERO;
        BigDecimal totalJugs = BigDecimal.ZERO;
        BigDecimal totalLiters = BigDecimal.ZERO;
        BigDecimal totalIncentive = BigDecimal.ZERO;

        for (Requisition requisition : requisitions) {
            for (RequisitionItem item : requisition.getItems()) {
                products.computeIfAbsent(
                                item.getProduct().getId(),
                                ignored -> new ProductAccumulator(item.getProduct(), managerStimulation, companyReport)
                        )
                        .add(item);
                totalItems = totalItems.add(item.getQuantity());
                if (item.getProduct().getPackageType() == PackageType.BOTTLE) {
                    totalBottles = totalBottles.add(item.getQuantity());
                } else if (item.getProduct().getPackageType() == PackageType.JUG) {
                    totalJugs = totalJugs.add(item.getQuantity());
                }
                totalLiters = totalLiters.add(itemLiters(item));
                totalIncentive = totalIncentive.add(itemIncentive(item, managerStimulation, companyReport));
            }
        }

        List<ProductSalesReportResponse> productRows = products.values().stream()
                .map(ProductAccumulator::toSalesResponse)
                .toList();
        SalesReportSummaryResponse summary = new SalesReportSummaryResponse(
                quantity(totalItems),
                quantity(totalBottles),
                quantity(totalJugs),
                liters(totalLiters),
                money(totalIncentive)
        );
        if (companyReport) {
            BigDecimal salesRepIncentive = requisitions.stream().flatMap(r -> r.getItems().stream())
                    .map(item -> itemIncentive(item, false)).reduce(ZERO_MONEY, BigDecimal::add);
            BigDecimal managerIncentive = requisitions.stream().flatMap(r -> r.getItems().stream())
                    .map(item -> itemIncentive(item, true)).reduce(ZERO_MONEY, BigDecimal::add);
            summary = new SalesReportSummaryResponse(summary.totalItems(), summary.totalBottles(),
                    summary.totalJugs(), summary.totalLiters(), money(salesRepIncentive.add(managerIncentive)),
                    money(salesRepIncentive), money(managerIncentive));
        }
        return new SalesAggregation(summary, productRows);
    }

    private static DateRange monthRange(YearMonth period) {
        ZoneId zone = ZoneId.systemDefault();
        return new DateRange(
                period.atDay(1).atStartOfDay(zone).toOffsetDateTime(),
                period.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime()
        );
    }

    private static DateRange yearRange(int year, OffsetDateTime now, ZoneId zone) {
        OffsetDateTime start = Year.of(year).atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = year == now.getYear()
                ? now
                : Year.of(year + 1).atDay(1).atStartOfDay(zone).toOffsetDateTime();
        return new DateRange(start, end);
    }

    private static BigDecimal itemLiters(RequisitionItem item) {
        return item.getQuantity().multiply(item.getProduct().getVolumeLiters());
    }

    private static BigDecimal itemIncentive(RequisitionItem item, boolean managerStimulation, boolean companyReport) {
        return companyReport ? itemIncentive(item, false).add(itemIncentive(item, true))
                : itemIncentive(item, managerStimulation);
    }

    private static BigDecimal itemIncentive(RequisitionItem item, boolean managerStimulation) {
        BigDecimal stimulationPerUnit = managerStimulation
                ? item.getProduct().getManagerStimulationPerUnit()
                : item.getProduct().getStimulationPerUnit();
        return item.getQuantity().multiply(stimulationPerUnit);
    }

    private static BigDecimal quantity(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    private static BigDecimal liters(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record DateRange(OffsetDateTime start, OffsetDateTime end) {
    }

    private record ReportScope(
            Long salesRepId,
            SalesRepresentativeOptionResponse salesRep,
            Long managerId,
            boolean teamReport,
            boolean managerStimulation,
            boolean organizationReport
    ) {
    }

    private record SalesAggregation(
            SalesReportSummaryResponse summary,
            List<ProductSalesReportResponse> products
    ) {
    }

    private record GratisAggregation(
            GratisReportSummaryResponse summary,
            List<GratisProductReportResponse> products,
            List<GratisReasonReportResponse> reasons
    ) {
    }

    private static final class ProductAccumulator {
        private final Product product;
        private final boolean managerStimulation;
        private final boolean companyReport;
        private BigDecimal units = BigDecimal.ZERO;
        private BigDecimal totalIncentive = BigDecimal.ZERO;

        private ProductAccumulator(Product product, boolean managerStimulation) {
            this(product, managerStimulation, false);
        }

        private ProductAccumulator(Product product, boolean managerStimulation, boolean companyReport) {
            this.product = product;
            this.managerStimulation = managerStimulation;
            this.companyReport = companyReport;
        }

        private void add(RequisitionItem item) {
            units = units.add(item.getQuantity());
            totalIncentive = totalIncentive.add(itemIncentive(item, managerStimulation, companyReport));
        }

        private ProductSalesReportResponse toSalesResponse() {
            BigDecimal incentivePerItem = units.signum() == 0
                    ? ZERO_MONEY
                    : totalIncentive.divide(units, 2, RoundingMode.HALF_UP);
            return new ProductSalesReportResponse(
                    product.getCode(),
                    product.getName(),
                    product.getPictureUrl(),
                    quantity(units),
                    product.getVolumeLiters(),
                    product.getPackageType(),
                    incentivePerItem,
                    money(totalIncentive)
            );
        }

        private GratisProductReportResponse toGratisResponse() {
            return new GratisProductReportResponse(
                    product.getCode(),
                    product.getName(),
                    product.getPictureUrl(),
                    quantity(units),
                    product.getVolumeLiters(),
                    product.getPackageType()
            );
        }
    }
}
