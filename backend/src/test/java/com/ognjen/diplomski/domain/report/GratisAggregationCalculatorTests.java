package com.ognjen.diplomski.domain.report;

import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.PackageType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GratisAggregationCalculatorTests {

    private final GratisAggregationCalculator calculator = new GratisAggregationCalculator();

    @Test
    void aggregatesGratisProductsPackagesLitersAndReasons() {
        Requisition gift = requisition(GratisReason.GIFT,
                item(product(1L, PackageType.BOTTLE, "0.750"), "2"));
        Requisition listing = requisition(GratisReason.LISTING,
                item(product(2L, PackageType.JUG, "5.000"), "1"));

        var result = calculator.aggregate(List.of(gift, listing));

        assertEquals(new BigDecimal("3"), result.summary().totalItems());
        assertEquals(new BigDecimal("2"), result.summary().totalBottles());
        assertEquals(new BigDecimal("1"), result.summary().totalJugs());
        assertEquals(new BigDecimal("6.500"), result.summary().totalLiters());
        assertEquals(2, result.products().size());
        assertEquals(
                new BigDecimal("1.500"),
                result.reasons().stream()
                        .filter(reason -> reason.reason() == GratisReason.GIFT)
                        .findFirst()
                        .orElseThrow()
                        .totalLiters()
        );
        assertEquals(
                new BigDecimal("5.000"),
                result.reasons().stream()
                        .filter(reason -> reason.reason() == GratisReason.LISTING)
                        .findFirst()
                        .orElseThrow()
                        .totalLiters()
        );
    }

    @Test
    void missingGratisReasonIsCountedAsOther() {
        Requisition requisition = requisition(null,
                item(product(1L, PackageType.BOTTLE, "1.000"), "3"));

        var result = calculator.aggregate(List.of(requisition));

        assertEquals(GratisReason.OTHER, result.reasons().getFirst().reason());
        assertEquals(new BigDecimal("3.000"), result.reasons().getFirst().totalLiters());
    }

    private Product product(Long id, PackageType packageType, String volume) {
        Product product = new Product();
        product.setId(id);
        product.setCode("P-" + id);
        product.setName("Product " + id);
        product.setPictureUrl("products/p-" + id + ".webp");
        product.setPackageType(packageType);
        product.setVolumeLiters(new BigDecimal(volume));
        return product;
    }

    private RequisitionItem item(Product product, String quantity) {
        RequisitionItem item = new RequisitionItem();
        item.setProduct(product);
        item.setQuantity(new BigDecimal(quantity));
        return item;
    }

    private Requisition requisition(GratisReason reason, RequisitionItem... items) {
        Requisition requisition = new Requisition();
        requisition.setGratisReason(reason);
        for (RequisitionItem item : items) {
            item.setRequisition(requisition);
            requisition.getItems().add(item);
        }
        return requisition;
    }
}
