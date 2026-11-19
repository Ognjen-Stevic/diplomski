package com.ognjen.diplomski.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPageResponse(
        List<ProductManagementResponse> content,
        long totalElements,
        int totalPages,
        int pageNumber
) {
    public static ProductPageResponse from(Page<ProductManagementResponse> products) {
        return new ProductPageResponse(
                products.getContent(),
                products.getTotalElements(),
                products.getTotalPages(),
                products.getNumber()
        );
    }
}
