package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.PackageType;

import java.math.BigDecimal;

public record ProductSalesReportResponse(
        String productCode,
        String productName,
        String pictureUrl,
        BigDecimal units,
        BigDecimal volumeLiters,
        PackageType packageType,
        BigDecimal incentivePerItem,
        BigDecimal totalIncentive
) {
}
