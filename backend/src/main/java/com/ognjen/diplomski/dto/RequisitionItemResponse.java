package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.RequisitionItem;
import com.ognjen.diplomski.entity.enums.PackageType;

import java.math.BigDecimal;

public record RequisitionItemResponse(
        String productCode,
        String productName,
        BigDecimal quantity,
        PackageType packageType,
        BigDecimal price,
        String pictureUrl,
        boolean active
) {
    public static RequisitionItemResponse from(RequisitionItem item) {
        return new RequisitionItemResponse(
                item.getProduct().getCode(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getProduct().getPackageType(),
                item.getProduct().getPrice(),
                item.getProduct().getPictureUrl(),
                item.getProduct().isActive()
        );
    }
}
