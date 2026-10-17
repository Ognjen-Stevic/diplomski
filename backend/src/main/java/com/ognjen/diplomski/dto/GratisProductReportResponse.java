package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.PackageType;

import java.math.BigDecimal;

public record GratisProductReportResponse(
        String productCode,
        String productName,
        String pictureUrl,
        BigDecimal units,
        BigDecimal volumeLiters,
        PackageType packageType
) {
}
