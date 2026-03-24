package com.sportcourt.core.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingDraftRequest(
    @NotNull UUID courtId,
    UUID customerId,
    @NotNull OffsetDateTime startTime,
    @NotNull OffsetDateTime endTime,
    @NotNull @Positive BigDecimal priceTotal
) {
}
