package com.sportcourt.core.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingDraftItemRequest(
    @NotNull UUID courtId,
    @NotNull OffsetDateTime startTime,
    @NotNull OffsetDateTime endTime,
    @Positive BigDecimal priceTotal
) {
}
