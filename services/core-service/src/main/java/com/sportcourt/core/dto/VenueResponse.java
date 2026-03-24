package com.sportcourt.core.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VenueResponse(
    UUID id,
    String name,
    String address,
    OffsetDateTime createdAt
) {
}
