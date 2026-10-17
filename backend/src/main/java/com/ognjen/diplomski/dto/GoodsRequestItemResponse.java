package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.PackageType;

import java.math.BigDecimal;

public record GoodsRequestItemResponse(
        String name,
        BigDecimal quantity,
        PackageType packageType
) {
}
