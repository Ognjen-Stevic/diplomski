package com.ognjen.diplomski.dto;

import java.util.List;

public record MonthlyRequisitionResponse(
        long totalCount,
        long gratisCount,
        long normalCount,
        List<RequisitionResponse> requisitions
) {
}
