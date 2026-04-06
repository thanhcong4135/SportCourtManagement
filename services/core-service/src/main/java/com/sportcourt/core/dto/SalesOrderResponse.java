package com.sportcourt.core.dto;

import com.sportcourt.core.domain.enums.SalesOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SalesOrderResponse(
    UUID id,
    UUID bookingId,
    UUID venueId,
    UUID customerId,
    SalesOrderStatus status,
    BigDecimal totalAmount,
    OffsetDateTime createdAt,
    List<SalesOrderItemResponse> items
) {
}
