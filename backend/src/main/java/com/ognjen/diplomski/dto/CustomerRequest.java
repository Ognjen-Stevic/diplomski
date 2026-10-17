package com.ognjen.diplomski.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank(message = "Naziv objekta je obavezan.")
        @Size(max = 300, message = "Naziv objekta može imati najviše 300 karaktera.")
        String objectName,

        @NotBlank(message = "Naziv kompanije je obavezan.")
        @Size(max = 300, message = "Naziv kompanije može imati najviše 300 karaktera.")
        String companyName,

        @NotBlank(message = "PIB je obavezan.")
        @Pattern(regexp = "\\d{9}", message = "PIB mora sadržati tačno 9 cifara.")
        String pib,

        @Size(max = 25, message = "Broj telefona može imati najviše 25 karaktera.")
        @Pattern(
                regexp = "^$|^\\+?[\\d\\s()/-]{6,25}$",
                message = "Broj telefona nije ispravan."
        )
        String phone,

        @Email(message = "E-mail adresa nije ispravna.")
        @Size(max = 254, message = "E-mail adresa može imati najviše 254 karaktera.")
        String email,

        @NotBlank(message = "Adresa je obavezna.")
        @Size(max = 300, message = "Adresa može imati najviše 300 karaktera.")
        String address,

        @Pattern(
                regexp = "(?i)^(?:$|https://(?:(?:[a-z0-9-]+\\.)*google\\.com|maps\\.app\\.goo\\.gl|goo\\.gl)(?::\\d+)?(?:[/?#].*)?)$",
                message = "Unesite ispravan Google Maps link."
        )
        String googleMapsUrl,

        @Size(max = 2000, message = "Napomena može imati najviše 2000 karaktera.")
        String note
) {
}
