package com.ognjen.diplomski.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ProductCreateRequest(
        @NotBlank(message = "Naziv je obavezna i može imati najviše 200 karaktera") String name,
        @NotBlank(message = "Šifra je obavezna i može imati najviše 100 karaktera") String code,
        @NotNull(message = "Cena mora biti veća od nule")
        @Positive(message = "Cena mora biti veća od nule") BigDecimal price,
        @NotNull(message = "Zapremina mora biti veća od nule")
        @Positive(message = "Zapremina mora biti veća od nule") BigDecimal volumeLiters,
        @NotNull(message = "Stimulacija komercijaliste ne može biti negativna")
        @PositiveOrZero(message = "Stimulacija komercijaliste ne može biti negativna") BigDecimal stimulationPerUnit,
        @NotNull(message = "Stimulacija menadžera ne može biti negativna")
        @PositiveOrZero(message = "Stimulacija menadžera ne može biti negativna") BigDecimal managerStimulationPerUnit
) {
}
