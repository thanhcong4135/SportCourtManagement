package com.sportcourt.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateVnpayPaymentRequest(
    @NotNull UUID bookingId,
    UUID customerId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @Size(max = 255) String customerName,
    @Size(max = 32) String customerPhone,
    @Size(max = 255) String orderInfo,
    @Size(max = 32) String bankCode,
    @Size(max = 128) String idempotencyKey
) {
}
