package com.sportcourt.core.dto;

import com.sportcourt.core.domain.enums.SportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CourtCreateRequest(
    @NotNull UUID venueId,
    @NotBlank @Size(max = 255) String name,
    @NotNull SportType sportType
) {
}
