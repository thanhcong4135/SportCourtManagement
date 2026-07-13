package com.sportcourt.payment.dto;

import com.sportcourt.payment.domain.enums.PaymentProvider;
import com.sportcourt.payment.domain.enums.PaymentTransactionStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentStatusByRefResponse(
    String paymentRef,
    UUID bookingId,
    BigDecimal amount,
    PaymentProvider provider,
    PaymentTransactionStatus status,
    String responseCode,
    String transactionStatus
) {
}
