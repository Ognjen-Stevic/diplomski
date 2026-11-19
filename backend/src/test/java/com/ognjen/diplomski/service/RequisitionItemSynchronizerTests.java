package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.RequisitionItemRequest;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RequisitionItemSynchronizerTests {

    private final ProductRepository products = mock(ProductRepository.class);
    private final RequisitionItemSynchronizer synchronizer = new RequisitionItemSynchronizer(products);

    @Test
    void updatesExistingItemAndRefreshesStimulationSnapshot() {
        Product product = product(1L, "P-1", true, "4.50");
        Requisition requisition = new Requisition();
        RequisitionItem existing = item(requisition, product, "1", "2.00");
        requisition.getItems().add(existing);
        when(products.findAllByCodeIn(any())).thenReturn(List.of(product));

        synchronizer.synchronize(
                requisition,
                List.of(new RequisitionItemRequest(" P-1 ", new BigDecimal("3")))
        );

        assertEquals(1, requisition.getItems().size());
        assertSame(existing, requisition.getItems().getFirst());
        assertEquals(new BigDecimal("3"), existing.getQuantity());
        assertEquals(new BigDecimal("4.50"), existing.getStimulationPerUnit());
    }

    @Test
    void createsRequestedItemAndRemovesMissingActiveItem() {
        Product removedProduct = product(1L, "P-1", true, "2.00");
        Product addedProduct = product(2L, "P-2", true, "5.00");
        Requisition requisition = new Requisition();
        requisition.getItems().add(item(requisition, removedProduct, "1", "2.00"));
        when(products.findAllByCodeIn(any())).thenReturn(List.of(addedProduct));

        synchronizer.synchronize(
                requisition,
                List.of(new RequisitionItemRequest("P-2", new BigDecimal("2")))
        );

        assertEquals(1, requisition.getItems().size());
        assertSame(addedProduct, requisition.getItems().getFirst().getProduct());
        assertEquals(new BigDecimal("5.00"), requisition.getItems().getFirst().getStimulationPerUnit());
    }

    @Test
    void rejectsDuplicateCodesBeforeProductLookup() {
        Requisition requisition = new Requisition();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> synchronizer.synchronize(requisition, List.of(
                        new RequisitionItemRequest("P-1", BigDecimal.ONE),
                        new RequisitionItemRequest(" P-1 ", new BigDecimal("2"))
                ))
        );

        assertEquals("A product may appear only once", error.getReason());
        verifyNoInteractions(products);
    }

    @Test
    void rejectsInvalidQuantityBeforeProductLookup() {
        Requisition requisition = new Requisition();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> synchronizer.synchronize(
                        requisition,
                        List.of(new RequisitionItemRequest("P-1", BigDecimal.ZERO))
                )
        );

        assertEquals("must be greater than or equal to 0.01", error.getReason());
        verifyNoInteractions(products);
    }

    @Test
    void rejectsNewInactiveProduct() {
        Product inactive = product(1L, "P-1", false, "2.00");
        when(products.findAllByCodeIn(any())).thenReturn(List.of(inactive));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> synchronizer.synchronize(
                        new Requisition(),
                        List.of(new RequisitionItemRequest("P-1", BigDecimal.ONE))
                )
        );

        assertEquals("An inactive product cannot be newly added", error.getReason());
    }

    private Product product(Long id, String code, boolean active, String stimulation) {
        Product product = new Product();
        product.setId(id);
        product.setCode(code);
        product.setName(code);
        product.setActive(active);
        product.setStimulationPerUnit(new BigDecimal(stimulation));
        return product;
    }

    private RequisitionItem item(
            Requisition requisition,
            Product product,
            String quantity,
            String stimulation
    ) {
        RequisitionItem item = new RequisitionItem();
        item.setRequisition(requisition);
        item.setProduct(product);
        item.setQuantity(new BigDecimal(quantity));
        item.setStimulationPerUnit(new BigDecimal(stimulation));
        return item;
    }
}
