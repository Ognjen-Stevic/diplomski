package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.PackageType;

import java.math.BigDecimal;

public record ProductResponse(
        String code,
        String name,
        BigDecimal price,
        String pictureUrl,
        PackageType packageType
) {
}
