package com.sportcourt.core.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VenueImageResponse(
    UUID id,
    UUID venueId,
    String imageUrl,
    String altText,
    int sortOrder,
    boolean cover,
    OffsetDateTime createdAt
) {
}
