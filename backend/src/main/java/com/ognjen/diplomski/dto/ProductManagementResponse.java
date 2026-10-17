package com.ognjen.diplomski.dto;

import java.math.BigDecimal;

public record ProductManagementResponse(
        Long id,
        String name,
        String code,
        String pictureUrl,
        BigDecimal price,
        BigDecimal volumeLiters,
        BigDecimal stimulationPerUnit,
        BigDecimal managerStimulationPerUnit,
        boolean active
) {
}
