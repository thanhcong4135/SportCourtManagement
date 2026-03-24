package com.sportcourt.core.dto;

import com.sportcourt.core.domain.enums.SportType;

import java.util.UUID;

public record CourtResponse(
    UUID id,
    UUID venueId,
    String name,
    SportType sportType,
    boolean active
) {
}
