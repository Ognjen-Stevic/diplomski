package com.ognjen.diplomski.dto;

import java.math.BigDecimal;

public record ProductCreateRequest(
        String name,
        String code,
        BigDecimal price,
        BigDecimal volumeLiters,
        BigDecimal stimulationPerUnit,
        BigDecimal managerStimulationPerUnit
) {
}
