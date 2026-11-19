package com.ognjen.diplomski.domain.report;

import com.ognjen.diplomski.dto.ManagerSalesComparisonResponse;
import com.ognjen.diplomski.dto.MonthlyIncentiveResponse;
import com.ognjen.diplomski.dto.ProductSalesReportResponse;
import com.ognjen.diplomski.dto.SalesReportSummaryResponse;
import com.ognjen.diplomski.dto.TeamSalesRepresentativeComparisonResponse;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.enums.PackageType;
import com.ognjen.diplomski.repository.SalesReportRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SalesAggregationCalculator {

    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);

    public SalesAggregation aggregate(
            List<Requisition> requisitions,
            boolean managerStimulation,
            boolean organizationReport
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
                                ignored -> new ProductAccumulator(
                                        item.getProduct(), managerStimulation, organizationReport
                                )
                        )
                        .add(item);
                totalItems = totalItems.add(item.getQuantity());
                if (item.getProduct().getPackageType() == PackageType.BOTTLE) {
                    totalBottles = totalBottles.add(item.getQuantity());
                } else if (item.getProduct().getPackageType() == PackageType.JUG) {
                    totalJugs = totalJugs.add(item.getQuantity());
                }
                totalLiters = totalLiters.add(itemLiters(item));
                totalIncentive = totalIncentive.add(
                        itemIncentive(item, managerStimulation, organizationReport)
                );
            }
        }

        List<ProductSalesReportResponse> productRows = products.values().stream()
                .map(ProductAccumulator::toResponse)
                .toList();
        SalesReportSummaryResponse summary = new SalesReportSummaryResponse(
                quantity(totalItems),
                quantity(totalBottles),
                quantity(totalJugs),
                liters(totalLiters),
                money(totalIncentive)
        );
        if (organizationReport) {
            BigDecimal salesRepIncentive = requisitions.stream()
                    .flatMap(requisition -> requisition.getItems().stream())
                    .map(item -> itemIncentive(item, false))
                    .reduce(ZERO_MONEY, BigDecimal::add);
            BigDecimal managerIncentive = requisitions.stream()
                    .flatMap(requisition -> requisition.getItems().stream())
                    .map(item -> itemIncentive(item, true))
                    .reduce(ZERO_MONEY, BigDecimal::add);
            summary = new SalesReportSummaryResponse(
                    summary.totalItems(),
                    summary.totalBottles(),
                    summary.totalJugs(),
                    summary.totalLiters(),
                    money(salesRepIncentive.add(managerIncentive)),
                    money(salesRepIncentive),
                    money(managerIncentive)
            );
        }
        return new SalesAggregation(summary, productRows);
    }

    public SalesAggregation aggregateTeamProducts(
            List<SalesReportRepository.TeamProductSalesProjection> rows
    ) {
        List<ProductSalesReportResponse> products = rows.stream()
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

    public SalesAggregation aggregateProductRows(
            List<SalesReportRepository.ProductSalesAggregateProjection> rows,
            boolean managerStimulation,
            boolean organizationReport
    ) {
        List<ProductSalesReportResponse> products = rows.stream()
                .map(row -> {
                    BigDecimal totalIncentive = selectedIncentive(
                            row.getSalesRepIncentive(),
                            row.getManagerIncentive(),
                            managerStimulation,
                            organizationReport
                    );
                    BigDecimal incentivePerItem = row.getUnits().signum() == 0
                            ? ZERO_MONEY
                            : totalIncentive.divide(row.getUnits(), 2, RoundingMode.HALF_UP);
                    return new ProductSalesReportResponse(
                            row.getProductCode(),
                            row.getProductName(),
                            row.getPictureUrl(),
                            quantity(row.getUnits()),
                            row.getVolumeLiters(),
                            row.getPackageType(),
                            money(incentivePerItem),
                            money(totalIncentive)
                    );
                })
                .toList();

        BigDecimal totalItems = BigDecimal.ZERO;
        BigDecimal totalBottles = BigDecimal.ZERO;
        BigDecimal totalJugs = BigDecimal.ZERO;
        BigDecimal totalLiters = BigDecimal.ZERO;
        BigDecimal salesRepIncentive = BigDecimal.ZERO;
        BigDecimal managerIncentive = BigDecimal.ZERO;
        for (int index = 0; index < products.size(); index++) {
            ProductSalesReportResponse product = products.get(index);
            SalesReportRepository.ProductSalesAggregateProjection row = rows.get(index);
            totalItems = totalItems.add(product.units());
            if (product.packageType() == PackageType.BOTTLE) {
                totalBottles = totalBottles.add(product.units());
            } else if (product.packageType() == PackageType.JUG) {
                totalJugs = totalJugs.add(product.units());
            }
            totalLiters = totalLiters.add(product.units().multiply(product.volumeLiters()));
            salesRepIncentive = salesRepIncentive.add(row.getSalesRepIncentive());
            managerIncentive = managerIncentive.add(row.getManagerIncentive());
        }

        BigDecimal totalIncentive = selectedIncentive(
                salesRepIncentive,
                managerIncentive,
                managerStimulation,
                organizationReport
        );
        SalesReportSummaryResponse summary = organizationReport
                ? new SalesReportSummaryResponse(
                        quantity(totalItems), quantity(totalBottles), quantity(totalJugs),
                        liters(totalLiters), money(totalIncentive),
                        money(salesRepIncentive), money(managerIncentive))
                : new SalesReportSummaryResponse(
                        quantity(totalItems), quantity(totalBottles), quantity(totalJugs),
                        liters(totalLiters), money(totalIncentive));
        return new SalesAggregation(summary, products);
    }

    public List<MonthlyIncentiveResponse> monthlyIncentives(
            List<Requisition> requisitions,
            int lastMonth,
            boolean managerStimulation,
            boolean organizationReport
    ) {
        Map<Integer, BigDecimal> incentivesByMonth = emptyMonthlyIncentives(lastMonth);
        for (Requisition requisition : requisitions) {
            BigDecimal incentive = requisition.getItems().stream()
                    .map(item -> itemIncentive(item, managerStimulation, organizationReport))
                    .reduce(ZERO_MONEY, BigDecimal::add);
            incentivesByMonth.merge(requisition.getCreatedAt().getMonthValue(), incentive, BigDecimal::add);
        }
        return toMonthlyIncentives(incentivesByMonth);
    }

    public List<MonthlyIncentiveResponse> teamMonthlyIncentives(
            List<SalesReportRepository.TeamMonthlyIncentiveProjection> rows,
            int lastMonth
    ) {
        Map<Integer, BigDecimal> incentivesByMonth = emptyMonthlyIncentives(lastMonth);
        rows.forEach(row -> incentivesByMonth.put(row.getMonth(), money(row.getTotalIncentive())));
        return toMonthlyIncentives(incentivesByMonth);
    }

    public List<MonthlyIncentiveResponse> monthlyIncentivesFromRows(
            List<SalesReportRepository.MonthlyIncentiveAggregateProjection> rows,
            int lastMonth,
            boolean managerStimulation,
            boolean organizationReport
    ) {
        Map<Integer, BigDecimal> incentivesByMonth = emptyMonthlyIncentives(lastMonth);
        rows.forEach(row -> incentivesByMonth.put(
                row.getMonth(),
                money(selectedIncentive(
                        row.getSalesRepIncentive(),
                        row.getManagerIncentive(),
                        managerStimulation,
                        organizationReport
                ))
        ));
        return toMonthlyIncentives(incentivesByMonth);
    }

    public List<TeamSalesRepresentativeComparisonResponse> teamComparison(
            List<SalesReportRepository.TeamSalesComparisonProjection> rows
    ) {
        return rows.stream()
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
    }

    public BigDecimal managerStimulation(
            List<SalesReportRepository.TeamSalesComparisonProjection> rows
    ) {
        return money(rows.stream()
                .map(SalesReportRepository.TeamSalesComparisonProjection::getManagerStimulation)
                .reduce(ZERO_MONEY, BigDecimal::add));
    }

    public List<ManagerSalesComparisonResponse> managersComparison(
            List<SalesReportRepository.ManagerSalesComparisonProjection> rows
    ) {
        return rows.stream()
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
    }

    private Map<Integer, BigDecimal> emptyMonthlyIncentives(int lastMonth) {
        Map<Integer, BigDecimal> incentivesByMonth = new LinkedHashMap<>();
        for (int month = 1; month <= lastMonth; month++) {
            incentivesByMonth.put(month, ZERO_MONEY);
        }
        return incentivesByMonth;
    }

    private List<MonthlyIncentiveResponse> toMonthlyIncentives(Map<Integer, BigDecimal> values) {
        return values.entrySet().stream()
                .map(entry -> new MonthlyIncentiveResponse(entry.getKey(), money(entry.getValue())))
                .toList();
    }

    private BigDecimal itemLiters(RequisitionItem item) {
        return item.getQuantity().multiply(item.getProduct().getVolumeLiters());
    }

    private BigDecimal itemIncentive(
            RequisitionItem item,
            boolean managerStimulation,
            boolean organizationReport
    ) {
        return organizationReport
                ? itemIncentive(item, false).add(itemIncentive(item, true))
                : itemIncentive(item, managerStimulation);
    }

    private BigDecimal itemIncentive(RequisitionItem item, boolean managerStimulation) {
        BigDecimal stimulationPerUnit = managerStimulation
                ? item.getProduct().getManagerStimulationPerUnit()
                : item.getProduct().getStimulationPerUnit();
        return item.getQuantity().multiply(stimulationPerUnit);
    }

    private BigDecimal selectedIncentive(
            BigDecimal salesRepIncentive,
            BigDecimal managerIncentive,
            boolean managerStimulation,
            boolean organizationReport
    ) {
        if (organizationReport) return salesRepIncentive.add(managerIncentive);
        return managerStimulation ? managerIncentive : salesRepIncentive;
    }

    private BigDecimal quantity(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    private BigDecimal liters(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record SalesAggregation(
            SalesReportSummaryResponse summary,
            List<ProductSalesReportResponse> products
    ) {
    }

    private final class ProductAccumulator {
        private final Product product;
        private final boolean managerStimulation;
        private final boolean organizationReport;
        private BigDecimal units = BigDecimal.ZERO;
        private BigDecimal totalIncentive = BigDecimal.ZERO;

        private ProductAccumulator(
                Product product,
                boolean managerStimulation,
                boolean organizationReport
        ) {
            this.product = product;
            this.managerStimulation = managerStimulation;
            this.organizationReport = organizationReport;
        }

        private void add(RequisitionItem item) {
            units = units.add(item.getQuantity());
            totalIncentive = totalIncentive.add(
                    itemIncentive(item, managerStimulation, organizationReport)
            );
        }

        private ProductSalesReportResponse toResponse() {
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
    }
}
