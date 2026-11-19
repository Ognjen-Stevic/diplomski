package com.ognjen.diplomski.domain.report;

import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.enums.PackageType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesAggregationCalculatorTests {

    private final SalesAggregationCalculator calculator = new SalesAggregationCalculator();

    @Test
    void aggregatesUnitsPackagesLitersAndSalesRepresentativeStimulation() {
        Product bottle = product(1L, PackageType.BOTTLE, "0.750", "8.00", "3.00");
        Product jug = product(2L, PackageType.JUG, "5.000", "10.00", "4.00");
        Requisition requisition = requisition("2026-06-15T10:00:00+02:00",
                item(bottle, "2"), item(jug, "1"));

        var result = calculator.aggregate(List.of(requisition), false, false);

        assertEquals(new BigDecimal("3"), result.summary().totalItems());
        assertEquals(new BigDecimal("2"), result.summary().totalBottles());
        assertEquals(new BigDecimal("1"), result.summary().totalJugs());
        assertEquals(new BigDecimal("6.500"), result.summary().totalLiters());
        assertEquals(new BigDecimal("26.00"), result.summary().totalIncentive());
        assertEquals(2, result.products().size());
    }

    @Test
    void organizationAggregationSeparatesAndCombinesBothStimulationTypes() {
        Product bottle = product(1L, PackageType.BOTTLE, "0.750", "8.00", "3.00");
        Requisition requisition = requisition("2026-06-15T10:00:00+02:00", item(bottle, "2"));

        var result = calculator.aggregate(List.of(requisition), false, true);

        assertEquals(new BigDecimal("22.00"), result.summary().totalIncentive());
        assertEquals(new BigDecimal("16.00"), result.summary().salesRepIncentive());
        assertEquals(new BigDecimal("6.00"), result.summary().managerIncentive());
        assertEquals(new BigDecimal("11.00"), result.products().getFirst().incentivePerItem());
    }

    @Test
    void monthlyIncentivesIncludeZeroMonthsAndAggregateMatchingMonth() {
        Product bottle = product(1L, PackageType.BOTTLE, "0.750", "8.00", "3.00");
        Requisition requisition = requisition("2026-02-15T10:00:00+01:00", item(bottle, "2"));

        var result = calculator.monthlyIncentives(List.of(requisition), 3, false, false);

        assertEquals(3, result.size());
        assertEquals(new BigDecimal("0.00"), result.get(0).totalIncentive());
        assertEquals(new BigDecimal("16.00"), result.get(1).totalIncentive());
        assertEquals(new BigDecimal("0.00"), result.get(2).totalIncentive());
    }

    private Product product(
            Long id,
            PackageType packageType,
            String volume,
            String salesRepStimulation,
            String managerStimulation
    ) {
        Product product = new Product();
        product.setId(id);
        product.setCode("P-" + id);
        product.setName("Product " + id);
        product.setPictureUrl("products/p-" + id + ".webp");
        product.setPackageType(packageType);
        product.setVolumeLiters(new BigDecimal(volume));
        product.setStimulationPerUnit(new BigDecimal(salesRepStimulation));
        product.setManagerStimulationPerUnit(new BigDecimal(managerStimulation));
        return product;
    }

    private RequisitionItem item(Product product, String quantity) {
        RequisitionItem item = new RequisitionItem();
        item.setProduct(product);
        item.setQuantity(new BigDecimal(quantity));
        return item;
    }

    private Requisition requisition(String createdAt, RequisitionItem... items) {
        Requisition requisition = new Requisition();
        requisition.setCreatedAt(OffsetDateTime.parse(createdAt));
        for (RequisitionItem item : items) {
            item.setRequisition(requisition);
            requisition.getItems().add(item);
        }
        return requisition;
    }
}
