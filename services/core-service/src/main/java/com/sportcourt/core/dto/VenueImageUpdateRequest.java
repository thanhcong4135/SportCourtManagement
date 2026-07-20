package com.sportcourt.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VenueImageUpdateRequest(
    @NotBlank @Size(max = 1024) String imageUrl,
    @Size(max = 255) String altText,
    Integer sortOrder,
    Boolean cover
) {
}
