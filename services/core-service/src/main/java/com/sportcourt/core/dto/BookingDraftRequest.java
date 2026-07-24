package com.sportcourt.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingDraftRequest(
    @NotNull UUID courtId,
    UUID customerId,
    @NotNull OffsetDateTime startTime,
    @NotNull OffsetDateTime endTime,
    @Positive BigDecimal priceTotal,
    @Email @Size(max = 128) String customerEmail
) {
    public BookingDraftRequest(UUID courtId,
                               UUID customerId,
                               OffsetDateTime startTime,
                               OffsetDateTime endTime,
                               BigDecimal priceTotal) {
        this(courtId, customerId, startTime, endTime, priceTotal, null);
    }
}
