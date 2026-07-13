package com.sportcourt.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VenueUpdateRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 500) String address
) {
}
