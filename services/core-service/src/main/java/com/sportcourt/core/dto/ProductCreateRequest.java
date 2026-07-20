package com.sportcourt.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductCreateRequest(
    @NotNull UUID venueId,
    @NotBlank String name,
    String description,
    @Size(max = 1024) String imageUrl,
    @Size(max = 64) String category,
    @Size(max = 64) String unit,
    @NotNull @Positive BigDecimal unitPrice,
    Boolean active
) {
    public ProductCreateRequest(UUID venueId, String name, BigDecimal unitPrice, Boolean active) {
        this(venueId, name, null, null, null, null, unitPrice, active);
    }
}
