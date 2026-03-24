package com.sportcourt.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateDepositPaymentRequest(
    @NotNull UUID bookingId,
    @NotNull UUID customerId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank String currency,
    @NotBlank String idempotencyKey
) {
}
