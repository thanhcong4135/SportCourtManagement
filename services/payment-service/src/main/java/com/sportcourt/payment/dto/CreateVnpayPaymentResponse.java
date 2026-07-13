package com.sportcourt.payment.dto;

import java.util.UUID;

public record CreateVnpayPaymentResponse(
    UUID paymentId,
    String paymentRef,
    String paymentUrl
) {
}
