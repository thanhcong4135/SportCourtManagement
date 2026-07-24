package com.sportcourt.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BatchBookingDraftRequest(
    UUID customerId,
    @NotEmpty List<@Valid BookingDraftItemRequest> items,
    @Email @Size(max = 128) String customerEmail
) {
    public BatchBookingDraftRequest(UUID customerId, List<BookingDraftItemRequest> items) {
        this(customerId, items, null);
    }
}
