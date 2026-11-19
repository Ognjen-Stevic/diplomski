package com.ognjen.diplomski.dto;

import java.util.List;

public record RequisitionSummaryPageResponse(
        long totalElements,
        int totalPages,
        int pageNumber,
        List<RequisitionListSummaryResponse> content
) {
}
