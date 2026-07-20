package com.sportcourt.core.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record VenueResponse(
    UUID id,
    String name,
    String address,
    String description,
    String coverImageUrl,
    String imageUrl,
    String phone,
    LocalTime openingTime,
    LocalTime closingTime,
    BigDecimal latitude,
    BigDecimal longitude,
    OffsetDateTime createdAt,
    List<VenueImageResponse> images
) {
}
