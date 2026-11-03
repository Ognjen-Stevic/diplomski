package com.ognjen.diplomski.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GoodsRequestRequest(
        @NotEmpty List<@NotNull @Valid GoodsRequestItemRequest> items,
        @Size(max = 5000) String note
) {
}
