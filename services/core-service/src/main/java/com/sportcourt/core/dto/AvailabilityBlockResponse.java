package com.sportcourt.core.dto;

import com.sportcourt.core.domain.enums.AvailabilityBlockStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AvailabilityBlockResponse(
    UUID courtId,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    AvailabilityBlockStatus status
) {
}
