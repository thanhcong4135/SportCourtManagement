package com.sportcourt.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record SalesOrderCreateRequest(
    UUID bookingId,
    UUID venueId,
    UUID customerId,
    @NotEmpty List<@Valid SalesOrderItemRequest> items
) {
}
