package com.sportcourt.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentCallbackRequest(
    @NotNull UUID paymentId,
    @NotBlank String providerReference,
    boolean success,
    String failureReason
) {
}
