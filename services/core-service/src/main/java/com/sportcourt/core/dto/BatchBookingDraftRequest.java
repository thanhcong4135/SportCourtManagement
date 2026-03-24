package com.sportcourt.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BatchBookingDraftRequest(
    UUID customerId,
    @NotEmpty List<@Valid BookingDraftItemRequest> items
) {
}
