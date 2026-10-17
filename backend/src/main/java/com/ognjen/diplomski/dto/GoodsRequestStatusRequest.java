package com.ognjen.diplomski.dto;

import com.ognjen.diplomski.entity.enums.GoodsRequestItemStatus;
import jakarta.validation.constraints.NotNull;

public record GoodsRequestStatusRequest(
        @NotNull GoodsRequestItemStatus status
) {
}
