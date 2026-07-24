package com.sportcourt.payment.dto;

import com.sportcourt.payment.domain.enums.PaymentTransactionStatus;
import com.sportcourt.payment.domain.enums.PaymentType;
import com.sportcourt.payment.domain.enums.PaymentProvider;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentTransactionResponse(
    UUID id,
    String paymentRef,
    UUID bookingId,
    UUID customerId,
    String customerEmail,
    BigDecimal amount,
    String currency,
    PaymentType type,
    PaymentTransactionStatus status,
    String idempotencyKey,
    PaymentProvider provider,
    String providerReference,
    String providerTransactionNo,
    String bankCode,
    String responseCode,
    String transactionStatus,
    String payDate,
    String checkoutUrl,
    OffsetDateTime requestedAt,
    OffsetDateTime updatedAt,
    OffsetDateTime completedAt,
    String failureReason
) {
}
