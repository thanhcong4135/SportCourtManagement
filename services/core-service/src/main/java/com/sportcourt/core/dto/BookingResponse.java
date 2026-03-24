package com.sportcourt.core.dto;

import com.sportcourt.core.domain.enums.BookingStatus;
import com.sportcourt.core.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingResponse(
    UUID id,
    UUID courtId,
    UUID customerId,
    BookingStatus status,
    PaymentStatus paymentStatus,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    BigDecimal priceTotal,
    BigDecimal depositRequired,
    BigDecimal depositPaid
) {
}
