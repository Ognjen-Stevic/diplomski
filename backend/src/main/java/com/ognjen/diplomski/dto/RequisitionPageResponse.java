package com.ognjen.diplomski.dto;

import java.util.List;

public record RequisitionPageResponse(
        long totalCount,
        long totalElements,
        long gratisCount,
        long normalCount,
        int totalPages,
        int pageNumber,
        List<RequisitionListSummaryResponse> content
) {
}
