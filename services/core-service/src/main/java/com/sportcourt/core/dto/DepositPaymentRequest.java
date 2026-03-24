package com.sportcourt.core.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositPaymentRequest(
    @NotNull @Positive BigDecimal amount
) {
}
