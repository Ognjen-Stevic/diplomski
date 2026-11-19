package com.ognjen.diplomski.domain.report;

import com.ognjen.diplomski.dto.GratisProductReportResponse;
import com.ognjen.diplomski.dto.GratisReasonReportResponse;
import com.ognjen.diplomski.dto.GratisReportSummaryResponse;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.PackageType;
import com.ognjen.diplomski.repository.SalesReportRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GratisAggregationCalculator {

    public GratisAggregation aggregate(List<Requisition> requisitions) {
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
                products.computeIfAbsent(product.getId(), ignored -> new ProductAccumulator(product))
                        .add(item);
                totalItems = totalItems.add(item.getQuantity());
                if (product.getPackageType() == PackageType.BOTTLE) {
                    totalBottles = totalBottles.add(item.getQuantity());
                } else if (product.getPackageType() == PackageType.JUG) {
                    totalJugs = totalJugs.add(item.getQuantity());
                }
                BigDecimal itemLiters = item.getQuantity().multiply(product.getVolumeLiters());
                totalLiters = totalLiters.add(itemLiters);
                requisitionLiters = requisitionLiters.add(itemLiters);
            }
            GratisReason reason = requisition.getGratisReason() == null
                    ? GratisReason.OTHER
                    : requisition.getGratisReason();
            litersByReason.merge(reason, requisitionLiters, BigDecimal::add);
        }

        List<GratisProductReportResponse> productRows = products.values().stream()
                .map(ProductAccumulator::toResponse)
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

    public GratisAggregation aggregateRows(
            List<SalesReportRepository.GratisProductAggregateProjection> productRows,
            List<SalesReportRepository.GratisReasonAggregateProjection> reasonRows
    ) {
        List<GratisProductReportResponse> products = productRows.stream()
                .map(row -> new GratisProductReportResponse(
                        row.getProductCode(),
                        row.getProductName(),
                        row.getPictureUrl(),
                        quantity(row.getUnits()),
                        row.getVolumeLiters(),
                        row.getPackageType()
                ))
                .toList();
        Map<GratisReason, BigDecimal> litersByReason = new EnumMap<>(GratisReason.class);
        reasonRows.forEach(row -> litersByReason.merge(
                row.getReason() == null ? GratisReason.OTHER : row.getReason(),
                row.getTotalLiters(),
                BigDecimal::add
        ));
        List<GratisReasonReportResponse> reasons = litersByReason.entrySet().stream()
                .map(entry -> new GratisReasonReportResponse(entry.getKey(), liters(entry.getValue())))
                .toList();

        BigDecimal totalItems = BigDecimal.ZERO;
        BigDecimal totalBottles = BigDecimal.ZERO;
        BigDecimal totalJugs = BigDecimal.ZERO;
        BigDecimal totalLiters = BigDecimal.ZERO;
        for (GratisProductReportResponse product : products) {
            totalItems = totalItems.add(product.units());
            if (product.packageType() == PackageType.BOTTLE) {
                totalBottles = totalBottles.add(product.units());
            } else if (product.packageType() == PackageType.JUG) {
                totalJugs = totalJugs.add(product.units());
            }
            totalLiters = totalLiters.add(product.units().multiply(product.volumeLiters()));
        }
        return new GratisAggregation(
                new GratisReportSummaryResponse(
                        quantity(totalItems), quantity(totalBottles), quantity(totalJugs),
                        liters(totalLiters)),
                products,
                reasons
        );
    }

    private BigDecimal quantity(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    private BigDecimal liters(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    public record GratisAggregation(
            GratisReportSummaryResponse summary,
            List<GratisProductReportResponse> products,
            List<GratisReasonReportResponse> reasons
    ) {
    }

    private final class ProductAccumulator {
        private final Product product;
        private BigDecimal units = BigDecimal.ZERO;

        private ProductAccumulator(Product product) {
            this.product = product;
        }

        private void add(RequisitionItem item) {
            units = units.add(item.getQuantity());
        }

        private GratisProductReportResponse toResponse() {
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
