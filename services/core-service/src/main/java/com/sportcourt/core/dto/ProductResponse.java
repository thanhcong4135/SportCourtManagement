package com.sportcourt.core.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    UUID venueId,
    String name,
    String description,
    String imageUrl,
    String category,
    String unit,
    BigDecimal unitPrice,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
