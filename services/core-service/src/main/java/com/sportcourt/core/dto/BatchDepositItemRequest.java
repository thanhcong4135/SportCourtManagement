package com.sportcourt.core.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record BatchDepositItemRequest(
    @NotNull UUID bookingId,
    @NotNull @Positive BigDecimal amount
) {
}
