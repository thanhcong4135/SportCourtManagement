package com.sportcourt.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductCreateRequest(
    @NotNull UUID venueId,
    @NotBlank String name,
    @NotNull @Positive BigDecimal unitPrice,
    Boolean active
) {
}
