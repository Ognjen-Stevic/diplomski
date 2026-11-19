package com.ognjen.diplomski.service;

import com.ognjen.diplomski.dto.RequisitionItemRequest;
import com.ognjen.diplomski.entity.Product;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RequisitionItemSynchronizer {

    private final ProductRepository productRepository;

    public void synchronize(Requisition requisition, List<RequisitionItemRequest> requestedItems) {
        validateQuantities(requestedItems);
        List<String> productCodes = requestedItems.stream()
                .map(RequisitionItemRequest::productCode)
                .map(String::trim)
                .toList();
        Set<String> uniqueCodes = new LinkedHashSet<>(productCodes);
        if (uniqueCodes.size() != productCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product may appear only once");
        }
        Map<String, Product> productsByCode = productRepository
                .findAllByCodeIn(uniqueCodes)
                .stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity()));
        if (productsByCode.size() != uniqueCodes.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "One or more products are unavailable"
            );
        }

        Map<Long, RequisitionItem> existingItemsByProductId = requisition.getItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getProduct().getId(),
                        Function.identity()
                ));
        Set<String> requestedCodes = new HashSet<>(productCodes);
        boolean removesInactiveExistingProduct = requisition.getItems().stream()
                .anyMatch(item -> !item.getProduct().isActive()
                        && !requestedCodes.contains(item.getProduct().getCode()));
        if (removesInactiveExistingProduct) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "An inactive product already in the requisition cannot be removed"
            );
        }

        Set<Long> requestedProductIds = new HashSet<>();
        for (String productCode : productCodes) {
            Product product = productsByCode.get(productCode);
            if (!product.isActive() && !existingItemsByProductId.containsKey(product.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "An inactive product cannot be newly added"
                );
            }
            if (!requestedProductIds.add(product.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A product may appear only once"
                );
            }
        }

        requisition.getItems().removeIf(
                item -> !requestedProductIds.contains(item.getProduct().getId())
        );
        for (int index = 0; index < requestedItems.size(); index++) {
            RequisitionItemRequest requestedItem = requestedItems.get(index);
            Product product = productsByCode.get(productCodes.get(index));
            RequisitionItem item = existingItemsByProductId.get(product.getId());
            if (item == null) {
                item = new RequisitionItem();
                item.setRequisition(requisition);
                item.setProduct(product);
                requisition.getItems().add(item);
            }
            item.setQuantity(requestedItem.quantity());
            item.setStimulationPerUnit(product.getStimulationPerUnit());
        }
    }

    private void validateQuantities(List<RequisitionItemRequest> requestedItems) {
        for (RequisitionItemRequest requestedItem : requestedItems) {
            if (requestedItem.quantity() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "must not be null");
            }
            if (requestedItem.quantity().compareTo(new BigDecimal("0.01")) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "must be greater than or equal to 0.01"
                );
            }
        }
    }
}
